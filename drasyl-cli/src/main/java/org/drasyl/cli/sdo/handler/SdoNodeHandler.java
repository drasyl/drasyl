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
package org.drasyl.cli.sdo.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.sdo.config.NetworkConfig;
import org.drasyl.cli.sdo.event.ConfigurationReceived;
import org.drasyl.cli.sdo.event.ControllerHandshakeCompleted;
import org.drasyl.cli.sdo.event.ControllerHandshakeFailed;
import org.drasyl.cli.sdo.message.JoinNetwork;
import org.drasyl.cli.sdo.message.NetworkJoinDenied;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.sdo.handler.SdoNodeHandler.State.CLOSING;
import static org.drasyl.cli.sdo.handler.SdoNodeHandler.State.CONNECTING;
import static org.drasyl.cli.sdo.handler.SdoNodeHandler.State.JOINED;
import static org.drasyl.cli.sdo.handler.SdoNodeHandler.State.JOINING;

public class SdoNodeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SdoNodeHandler.class);
    private final IdentityPublicKey controller;
    private State state = CONNECTING;
    private DrasylChannel controllerChannel;
    private NetworkConfig config;

    public SdoNodeHandler(final IdentityPublicKey controller) {
        this.controller = requireNonNull(controller);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        LOG.debug("Connect to controller `{}`", controller);
        ((DrasylServerChannel) ctx.channel()).serve(controller).addListener((GenericFutureListener<Future<DrasylChannel>>) future -> controllerChannel = future.getNow());
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (state == CONNECTING && evt instanceof ControllerHandshakeFailed) {
            final Throwable cause = ((ControllerHandshakeFailed) evt).cause();
            LOG.error("Controller handshake failed: ", cause);
            ctx.fireExceptionCaught(cause);
        }
        else if (state == CONNECTING && evt instanceof ControllerHandshakeCompleted) {
            LOG.debug("Connected to controller. Join network.");
            state = JOINING;
            controllerChannel.writeAndFlush(new JoinNetwork()).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else if (state == JOINING && evt instanceof ConfigurationReceived) {
            LOG.debug("Got configuration. Network join succeeded.");
            state = JOINED;
            config = ((ConfigurationReceived) evt).config();
            ctx.pipeline().addLast(new NetworkConfigHandler(config));
        }
        else if (state == JOINING && evt instanceof NetworkJoinDenied) {
            LOG.debug("Controller declined our join request. Shutdown.");
            state = CLOSING;
            ctx.channel().parent().close();
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    enum State {
        CONNECTING,
        JOINING,
        JOINED,
        CLOSING
    }
}
