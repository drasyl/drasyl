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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * This handler passes messages from the {@link io.netty.channel.socket.SocketChannel} to the
 * {@link org.drasyl.channel.DrasylServerChannel}'s context.
 */
@UnstableApi
public class TcpClientToDrasylHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TcpClientToDrasylHandler.class);
    private final ChannelHandlerContext drasylCtx;

    TcpClientToDrasylHandler(final ChannelHandlerContext drasylCtx) {
        this.drasylCtx = requireNonNull(drasylCtx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext nettyCtx,
                            final Object msg) {
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
