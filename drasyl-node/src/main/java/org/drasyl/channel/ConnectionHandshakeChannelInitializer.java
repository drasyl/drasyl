/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.drasyl.handler.connection.ConnectionClosing;
import org.drasyl.handler.connection.ConnectionException;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ReliableConnectionConfig;
import org.drasyl.handler.connection.ReliableConnectionHandler;
import org.drasyl.handler.connection.SegmentCodec;

import static java.util.Objects.requireNonNull;

public abstract class ConnectionHandshakeChannelInitializer extends ChannelInitializer<DrasylChannel> {
    protected final ReliableConnectionConfig config;

    protected ConnectionHandshakeChannelInitializer(final ReliableConnectionConfig config) {
        this.config = requireNonNull(config);
    }

    protected ConnectionHandshakeChannelInitializer() {
        this(ReliableConnectionConfig.newBuilder().build());
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new SegmentCodec());
        p.addLast(new ReliableConnectionHandler(config));

        p.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
        p.addLast(new LengthFieldPrepender(4));

        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) throws Exception {
                if (evt instanceof ConnectionHandshakeCompleted) {
                    handshakeCompleted((DrasylChannel) ctx.channel());
                }
                else if (evt instanceof ConnectionClosing && ((ConnectionClosing) evt).initatedByRemotePeer()) {
                    // confirm close request
                    ctx.pipeline().close();
                }
                else {
                    ctx.fireUserEventTriggered(evt);
                }
            }

            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx,
                                        final Throwable cause) {
                if (cause instanceof ConnectionException) {
                    // FIXME: das gibt es gar nicht mehr
                    handshakeFailed(ctx, cause);
                }
                else {
                    ctx.fireExceptionCaught(cause);
                }
            }
        });
    }

    @SuppressWarnings("java:S112")
    protected abstract void handshakeCompleted(final DrasylChannel ch) throws Exception;

    protected abstract void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause);
}
