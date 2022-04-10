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
package org.drasyl.handler.connection;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.drasyl.handler.connection.ConnectionHandshakeHandler.UserCall;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectionHandshakeHandlerIT {
    @Test
    void passiveOpenCompleted() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(20_000, false));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    latch.countDown();
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(20_000, false));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    latch.countDown();
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            // initiate handshake
            clientChannel.writeAndFlush(UserCall.OPEN).sync();

            // wait for completion
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void activeOpenCompleted() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(20_000, true));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    latch.countDown();
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(20_000, true));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    latch.countDown();
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            // wait for completion
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void activeOpenTimeout() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(1_000, true));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(final ChannelHandlerContext ctx,
                                                        final Throwable cause) {
                                if (cause instanceof ConnectionHandshakeException) {
                                    latch.countDown();
                                }
                                ctx.fireExceptionCaught(cause);
                            }
                        });
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            // wait for completion
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
