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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
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
import org.drasyl.util.internal.UnstableApi;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.TransmissionControlBlock.MAX_PORT;
import static org.drasyl.handler.connection.TransmissionControlBlock.MIN_PORT;
import static org.drasyl.util.Preconditions.requireInRange;

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
    public static final int DEFAULT_SERVER_PORT = 21_037;
    private final int listenPort;
    private final boolean overrideActiveOpen;
    protected final ConnectionConfig config;

    protected ConnectionChannelInitializer(final int listenPort,
                                           final boolean overrideActiveOpen,
                                           final ConnectionConfig config) {
        this.listenPort = requireInRange(listenPort, MIN_PORT, MAX_PORT);
        this.overrideActiveOpen = overrideActiveOpen;
        this.config = requireNonNull(config);
    }

    protected ConnectionChannelInitializer(final boolean overrideActiveOpen,
                                           final ConnectionConfig config) {
        this(DEFAULT_SERVER_PORT, overrideActiveOpen, config);
    }

    protected ConnectionChannelInitializer(final boolean overrideActiveOpen) {
        this(overrideActiveOpen, ConnectionConfig.newBuilder().build());
    }

    protected ConnectionChannelInitializer() {
        this(true);
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new SegmentCodec());

        final boolean iAmServer = iAmServer(ch);
        final int localPort;
        final int remotePort;
        if (iAmServer) {
            // I'm the "server"
            localPort = listenPort;
            remotePort = 0;
        }
        else {
            // I'm the "client"
            localPort = 0;
            remotePort = listenPort;
        }

        final ConnectionConfig overriddenConfig;
        if (overrideActiveOpen) {
            overriddenConfig = config.toBuilder().activeOpen(!iAmServer).build();
        }
        else {
            overriddenConfig = config;
        }
        p.addLast(new ConnectionHandler(localPort, remotePort, overriddenConfig));

        // FIXME: remove when debugging is done
        p.addLast(new ConnectionAnalyzeHandler());

        p.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
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
                    handshakeFailed(ctx, cause);
                }
                else {
                    ctx.fireExceptionCaught(cause);
                }
            }
        });
    }

    private static boolean iAmServer(final DrasylChannel ch) {
        return Integer.signum(Arrays.compareUnsigned(((DrasylAddress) ch.localAddress()).toByteArray(), ((DrasylAddress) ch.remoteAddress0()).toByteArray())) == -1;
    }

    @SuppressWarnings("java:S112")
    protected abstract void handshakeCompleted(final DrasylChannel ch) throws Exception;

    protected abstract void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause);
}
