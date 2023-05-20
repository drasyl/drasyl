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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.drasyl.handler.remote.ByteToRemoteMessageCodec;
import org.drasyl.handler.remote.InvalidProofOfWorkFilter;
import org.drasyl.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@UnstableApi
public class TcpServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ChannelHandlerContext ctx;

    public TcpServerChannelInitializer(final ChannelHandlerContext ctx) {
        this.ctx = requireNonNull(ctx);
    }

    @Override
    protected void initChannel(final SocketChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        final TcpServer tcpServer = (TcpServer) ctx.handler();

        p.addLast(new IdleStateHandler(tcpServer.pingTimeout().toMillis(), 0, 0, MILLISECONDS));
        p.addLast(new ByteBufCodec());
        p.addLast(new TcpCloseIdleClientsHandler());
        p.addLast(new TcpDrasylMessageHandler());
        p.addLast(new ByteToRemoteMessageCodec());
        p.addLast(new InvalidProofOfWorkFilter());
        lastStage(ch);
    }

    protected void lastStage(final SocketChannel ch) {
        final TcpServer tcpServer = (TcpServer) ctx.handler();

        ch.pipeline().addLast(new TcpServerHandler(tcpServer.clientChannels(), ctx));
    }
}
