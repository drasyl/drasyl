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
import org.drasyl.handler.connection.ConnectionClosing;
import org.drasyl.handler.connection.ConnectionConfig;
import org.drasyl.handler.connection.ConnectionException;
import org.drasyl.handler.connection.ConnectionHandler;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.SegmentCodec;
import org.drasyl.util.internal.UnstableApi;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.TransmissionControlBlock.MAX_PORT;
import static org.drasyl.util.Preconditions.requireInRange;

/**
 * This {@link ChannelInitializer} create a channel providing reliable and ordered delivery of bytes
 * between hosts. Handlers can either be added to the pipeline on channel creation time using
 * {@link #initChannel(DrasylChannel)} or after connection with the remote peer has been established
 * using {@link #handshakeCompleted(ChannelHandlerContext)}.
 * {@link #handshakeFailed(ChannelHandlerContext, Throwable)} is called, when no connection could be
 * established (e.g., because the other party has not responded within the time limit or has
 * rejected the handshake).
 */
@UnstableApi
public abstract class ConnectionChannelInitializer extends ChannelInitializer<DrasylChannel> {
    public static final int DEFAULT_SERVER_PORT = 21_037;
    private int localPort;
    private int remotePort;
    protected final ConnectionConfig config;

    protected ConnectionChannelInitializer(final int localPort,
                                           final int remotePort,
                                           final ConnectionConfig config) {
        this.localPort = requireInRange(localPort, 0, MAX_PORT);
        this.remotePort = requireInRange(remotePort, 0, MAX_PORT);
        this.config = requireNonNull(config);
    }

    protected ConnectionChannelInitializer(final int localPort,
                                           final int remotePort) {
        this(localPort, remotePort, ConnectionConfig.newBuilder().build());
    }

    /**
     * @param doServer Determines the server behavior:<br>
     *                 <ul>
     *                 <li>{@code true} sets this channel to server mode, listening on the specified {@code port}.</li>
     *                 <li>{@code false} sets this channel to client mode, listening on a random port while assuming the peer listens on {@code port}.</li>
     *                 </ul>
     * @param port     Specifies the port number. In server mode, the channel listens on this port.
     *                 In client mode, the channel assumes the peer listens on this port.
     * @param config   Configuration settings for connections.
     */
    protected ConnectionChannelInitializer(final boolean doServer,
                                           final int port,
                                           final ConnectionConfig config) {
        if (doServer) {
            // I'm the "server"
            localPort = requireInRange(port, 0, MAX_PORT);
            remotePort = 0;
            this.config = config.toBuilder().activeOpen(false).build();
        }
        else {
            // I'm the "client"
            localPort = 0;
            remotePort = requireInRange(port, 0, MAX_PORT);
            this.config = config.toBuilder().activeOpen(true).build();
        }
    }

    /**
     * @param doServer Determines the server behavior:<br>
     *                 <ul>
     *                 <li>{@code true} sets this channel to server mode, listening on the specified {@code port}.</li>
     *                 <li>{@code false} sets this channel to client mode, listening on a random port while assuming the peer listens on {@code port}.</li>
     *                 <li>{@code null} decides the server/client role based on comparing local and remote public keys, with the "higher" key indicating a server.</li>
     *                 </ul>
     * @param port     Specifies the port number. In server mode, the channel listens on this port.
     *                 In client mode, the channel assumes the peer listens on this port.
     */
    protected ConnectionChannelInitializer(final Boolean doServer, final int port) {
        this(doServer, port, ConnectionConfig.newBuilder().build());
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new SegmentCodec());
        p.addLast(new ConnectionHandler(localPort, remotePort, config));
        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) throws Exception {
                if (evt instanceof ConnectionHandshakeCompleted) {
                    handshakeCompleted(ctx);
                }
                else if (evt instanceof ConnectionClosing && ((ConnectionClosing) evt).initatedByRemotePeer()) {
                    // confirm close request
                    ctx.pipeline().close().addListener(FIRE_EXCEPTION_ON_FAILURE);
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
