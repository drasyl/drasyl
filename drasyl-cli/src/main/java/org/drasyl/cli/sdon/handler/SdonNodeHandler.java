/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.sdon.config.NetworkConfig;
import org.drasyl.cli.sdon.config.Policy;
import org.drasyl.cli.sdon.event.SdonMessageReceived;
import org.drasyl.cli.sdon.message.AccessDenied;
import org.drasyl.cli.sdon.message.ControllerHello;
import org.drasyl.cli.sdon.message.NodeHello;
import org.drasyl.cli.sdon.message.SdonMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.sdon.handler.SdonNodeHandler.State.CLOSING;
import static org.drasyl.cli.sdon.handler.SdonNodeHandler.State.INITIALIZED;
import static org.drasyl.cli.sdon.handler.SdonNodeHandler.State.JOINED;
import static org.drasyl.cli.sdon.handler.SdonNodeHandler.State.JOINING;

public class SdonNodeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SdonNodeHandler.class);
    private final IdentityPublicKey controller;
    State state;
    private DrasylChannel controllerChannel;
    private NetworkConfig config;

    public SdonNodeHandler(final IdentityPublicKey controller) {
        this.controller = requireNonNull(controller);
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

            System.out.println("----------------------------------------------------------------------------------------------");
            System.out.println("Node listening on address " + ctx.channel().localAddress());
            System.out.println("----------------------------------------------------------------------------------------------");

            LOG.info("Connecting to controller `{}`", controller);
            ((DrasylServerChannel) ctx.channel()).serve(controller).addListener((GenericFutureListener<Future<DrasylChannel>>) future -> {
                controllerChannel = future.getNow();

                if (state == INITIALIZED) {
                    LOG.info("Connected to controller. Try to join network.");
                    state = JOINING;
                    controllerChannel.eventLoop().execute(() -> {
                        controllerChannel.writeAndFlush(new NodeHello(Set.of(), new HashMap<>())).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    });
                }
            });
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof SdonMessageReceived) {
            final DrasylAddress sender = ((SdonMessageReceived) evt).node();
            final SdonMessage msg = ((SdonMessageReceived) evt).msg();
            LOG.debug("Received from `{}`: {}", sender, msg);

            if (sender.equals(controller) && msg instanceof ControllerHello) {
                if (state != JOINED) {
                    LOG.info("Joined network.");
                }
                state = JOINED;
                final Set<Policy> newPolicies = ((ControllerHello) msg).policies();
                LOG.trace("Got new policies from controller: {}", newPolicies);
                final SdonPoliciesHandler handler = ctx.pipeline().get(SdonPoliciesHandler.class);
                handler.newPolicies(newPolicies);
            }
            else if (sender.equals(controller) && msg instanceof AccessDenied) {
                LOG.error("Controller declined our join request. Shutdown.");
                state = CLOSING;
                ctx.channel().close();
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
