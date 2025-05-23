/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.cli.sdon.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.sdon.config.Policy;
import org.drasyl.cli.sdon.event.SdonMessageReceived;
import org.drasyl.cli.sdon.message.ControllerHello;
import org.drasyl.cli.sdon.message.DeviceHello;
import org.drasyl.cli.sdon.message.SdonMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.cli.sdon.handler.SdonDeviceHandler.State.INITIALIZED;
import static org.drasyl.cli.sdon.handler.SdonDeviceHandler.State.JOINED;
import static org.drasyl.cli.sdon.handler.SdonDeviceHandler.State.JOINING;
import static org.drasyl.util.RandomUtil.randomInt;

public class SdonDeviceHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SdonDeviceHandler.class);
    private static final int DEVICE_HELLO_INTERVAL = 5_000;
    private final PrintStream out;
    private final IdentityPublicKey controller;
    private final Map<String, Object> facts;
    State state;
    public final Set<Policy> policies = new HashSet<>();

    public SdonDeviceHandler(final PrintStream out,
                             final IdentityPublicKey controller,
                             final Map<String, Object> facts) {
        this.out = requireNonNull(out);
        this.controller = requireNonNull(controller);
        this.facts = requireNonNull(facts);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            ensureHandlerInitialized(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ensureHandlerInitialized(ctx);
        ctx.fireChannelActive();
    }

    private void ensureHandlerInitialized(final ChannelHandlerContext ctx) {
        if (state == null) {
            state = INITIALIZED;

            out.println("----------------------------------------------------------------------------------------------");
            out.println("Device listening on address " + ctx.channel().localAddress());
            out.println("----------------------------------------------------------------------------------------------");

            out.print("Connecting to controller " + controller + "...");
            ((DrasylServerChannel) ctx.channel()).serve(controller).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    if (state == INITIALIZED) {
                        final Channel channel = future.channel();
                        out.println("connected!");
                        out.print("Register at controller...");
                        state = JOINING;
                        final DeviceHello hello = new DeviceHello(facts, policies);
                        LOG.debug("Send `{}`", hello);
                        channel.writeAndFlush(hello).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    }
                }
            });

            ctx.executor().scheduleAtFixedRate(() -> ((DrasylServerChannel) ctx.channel()).serve(controller).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    if (state == JOINED) {
                        final Channel channel = future.channel();
                        final DeviceHello hello = new DeviceHello(facts, policies);
                        LOG.debug("Send `{}`", hello);
                        channel.writeAndFlush(hello).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    }
                }
            }), randomInt(0, DEVICE_HELLO_INTERVAL), DEVICE_HELLO_INTERVAL, MILLISECONDS);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof SdonMessageReceived) {
            final DrasylAddress sender = ((SdonMessageReceived) evt).address();
            final SdonMessage msg = ((SdonMessageReceived) evt).msg();
            LOG.debug("Received from `{}`: {}", sender, msg);

            if (sender.equals(controller) && msg instanceof ControllerHello) {
                if (state != JOINED) {
                    out.println("registered!");
                }
                state = JOINED;

                final Set<Policy> newPolicies = ((ControllerHello) msg).policies();
                LOG.trace("Got new policies from controller: {}", newPolicies);

                // remove old policies
                for (final Policy policy : policies) {
                    if (!newPolicies.contains(policy)) {
                        LOG.debug("Remove old policy: {}", policy);
                        policy.removePolicy(ctx.pipeline());
                    }
                }

                // add new policies
                for (final Policy newPolicy : newPolicies) {
                    if (!policies.contains(newPolicy)) {
                        LOG.debug("Add new policy: {}", newPolicy);
                        newPolicy.addPolicy(ctx.pipeline());
                    }
                }

                policies.clear();
                policies.addAll(newPolicies);
            }
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    enum State {
        INITIALIZED,
        JOINING,
        JOINED,
        CLOSING
    }
}
