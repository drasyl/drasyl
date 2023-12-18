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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.drasyl.handler.connection.ConnectionAnalyzeHandler;
import org.drasyl.handler.connection.ConnectionClosing;
import org.drasyl.handler.connection.ConnectionConfig;
import org.drasyl.handler.connection.ConnectionException;
import org.drasyl.handler.connection.ConnectionHandler;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.SegmentCodec;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.ByteUtil;
import org.drasyl.util.internal.UnstableApi;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * This {@link ChannelInitializer} create a channel providing reliable and ordered delivery of bytes
 * between hosts. Handlers can either be added to the pipeline on channel creation time using
 * {@link #initChannel(DrasylChannel)} or after connection with the remote peer has been established
 * using {@link #handshakeCompleted(DrasylChannel)}.
 * {@link #handshakeFailed(ChannelHandlerContext, Throwable)} is called, when no connection could be
 * established (e.g., because the other party has not responded within the time limit or has
 * rejected the handshake).
 */
@UnstableApi
public abstract class ConnectionChannelInitializer extends ChannelInitializer<DrasylChannel> {
    protected final ConnectionConfig config;

    protected ConnectionChannelInitializer(final ConnectionConfig config) {
        this.config = requireNonNull(config);
    }

    protected ConnectionChannelInitializer() {
        this(ConnectionConfig.newBuilder().build());
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        //p.addLast(new MagicNumberBasedFrameDecoder(1, 123));
        //p.addLast(new MagicNumberPrepender(1, 123));
        p.addLast(new SegmentCodec());

        final ConnectionConfig myConf;
        if (Integer.signum(Arrays.compareUnsigned(((DrasylAddress) ch.localAddress()).toByteArray(), ((DrasylAddress) ch.remoteAddress0()).toByteArray())) == 1) {
            myConf =config.toBuilder().activeOpen(true).build();
        }
        else {
            myConf =config.toBuilder().activeOpen(false).build();
        }

        p.addLast(new ConnectionHandler(myConf));
        if (false) {
            p.addLast(new ConnectionAnalyzeHandler());
        }
        p.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        p.addLast(new LengthFieldPrepender(4));

        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) throws Exception {
                if (evt instanceof ConnectionHandshakeCompleted) {
                    handshakeCompleted(ctx);
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
                    handshakeFailed(ctx, cause);
                }
                else {
                    ctx.fireExceptionCaught(cause);
                }
            }
        });
    }

    @SuppressWarnings("java:S112")
    protected abstract void handshakeCompleted(final ChannelHandlerContext ctx) throws Exception;

    protected abstract void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause);
}
