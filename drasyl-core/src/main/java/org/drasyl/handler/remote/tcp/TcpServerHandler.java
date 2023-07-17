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
package org.drasyl.handler.remote.tcp;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This handler passes all receiving messages to the pipeline and updates {@link #clients} on
 * new/closed connections.
 */
@UnstableApi
public
class TcpServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TcpServerHandler.class);
    private final Map<SocketAddress, Channel> clients;
    private final ChannelHandlerContext drasylCtx;

    TcpServerHandler(final Map<SocketAddress, Channel> clients,
                     final ChannelHandlerContext drasylCtx) {
        this.clients = requireNonNull(clients);
        this.drasylCtx = requireNonNull(drasylCtx);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        LOG.debug("New TCP connection from client `{}`.", ctx.channel()::remoteAddress);
        clients.put(ctx.channel().remoteAddress(), ctx.channel());

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        LOG.debug("TCP connection to client `{}` closed.", ctx.channel()::remoteAddress);
        clients.remove(ctx.channel().remoteAddress());

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        LOG.trace("Packet `{}` received via TCP from `{}`", () -> msg, ctx.channel()::remoteAddress);
        drasylCtx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        drasylCtx.fireChannelReadComplete();
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        LOG.debug("Close TCP connection to `{}` due to an exception: ", ctx.channel()::remoteAddress, () -> cause);
        ctx.close();
    }
}
