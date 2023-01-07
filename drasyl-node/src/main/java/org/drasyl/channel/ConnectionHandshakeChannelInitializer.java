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
import io.netty.channel.DefaultEventLoop;
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakeStatus;
import org.drasyl.util.CsvLogger;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public abstract class ConnectionHandshakeChannelInitializer extends ChannelInitializer<DrasylChannel> {
    public static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);
    protected final Duration handshakeTimeout;
    protected final boolean initiateHandshake;

    protected ConnectionHandshakeChannelInitializer(final Duration handshakeTimeout,
                                                    final boolean initiateHandshake) {
        this.handshakeTimeout = handshakeTimeout;
        this.initiateHandshake = initiateHandshake;
    }

    protected ConnectionHandshakeChannelInitializer(final boolean initiateHandshake) {
        this(DEFAULT_HANDSHAKE_TIMEOUT, initiateHandshake);
    }

    @SuppressWarnings("java:S1188")
    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        final ChannelPipeline p = ch.pipeline();

        p.addLast(new ConnectionHandshakeCodec());
        final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(handshakeTimeout, initiateHandshake);
        p.addLast(handler);
        final CsvLogger logger = new CsvLogger("/Users/heiko/Development/drasyl/" + StringUtil.simpleClassName(this) + "-" + CsvLogger.PID + ".csv");
        new DefaultEventLoop().scheduleAtFixedRate(() -> {
            try {
                ConnectionHandshakeStatus status = handler.userCallStatus();
                logger.log(status.tcb());
            }
            catch (final ClosedChannelException e) {
                e.printStackTrace();
            }
        }, 100, 100, MILLISECONDS);
        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx,
                                           final Object evt) throws Exception {
                if (evt instanceof ConnectionHandshakeCompleted) {
                    handshakeCompleted((DrasylChannel) ctx.channel());

                    ctx.pipeline().remove(this);
                }
                else {
                    ctx.fireUserEventTriggered(evt);
                }
            }

            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx,
                                        final Throwable cause) {
                if (cause instanceof ConnectionHandshakeException) {
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
