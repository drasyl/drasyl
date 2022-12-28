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
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.RandomUtil;
import org.junit.jupiter.api.Test;
import test.DropRandomOutboundMessagesHandler;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionHandshakeHandlerIT {
    private static final float LOSS_RATE = 0.2f;
    private static final int MAX_DROP = 3;

    @Test
    void passiveOpenCompleted() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new DropRandomOutboundMessagesHandler(LOSS_RATE, MAX_DROP));
                        p.addLast(new ConnectionHandshakeHandler(Duration.ofHours(1), false));
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new DropRandomOutboundMessagesHandler(LOSS_RATE, MAX_DROP));
                        p.addLast(new ConnectionHandshakeHandler(Duration.ofHours(1), true));
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

        // FIXME: diesen dead so lange wiederholen, bis repackage/optimize wird notwendig, weil mehrere segmente gleichzeitig gelesen wurden

        // server
        final AtomicReference<ConnectionHandshakeHandler> serverHandler = new AtomicReference<>();
        final AtomicReference<ConnectionHandshakeHandler> clientHandler = new AtomicReference<>();
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
//                        p.addLast(new DropRandomOutboundMessagesHandler(LOSS_RATE, MAX_DROP));
                        serverHandler.set(new ConnectionHandshakeHandler(Duration.ofHours(1), true));
                        p.addLast(serverHandler.get());
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
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
//                        p.addLast(new DropRandomOutboundMessagesHandler(LOSS_RATE, MAX_DROP));
                        clientHandler.set(new ConnectionHandshakeHandler(Duration.ofHours(1), true));
                        p.addLast(clientHandler.get());
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

            for (int i = 0; i < 1; i++) {
                final ByteBuf buffer = clientChannel.alloc().buffer(5000);
                buffer.writeBytes(RandomUtil.randomBytes(5000));
                final ChannelFuture channelFuture = clientChannel.writeAndFlush(buffer).syncUninterruptibly();
                assertTrue(channelFuture.isSuccess());
                System.err.println(System.currentTimeMillis() + " ConnectionHandshakeHandlerIT.activeOpenCompleted");
                assertEquals(0, serverHandler.get().sendBuffer.readableBytes());
                assertEquals(0, serverHandler.get().retransmissionQueue.size());
                assertEquals(0, serverHandler.get().receiveBuffer.readableBytes());
                assertEquals(0, clientHandler.get().sendBuffer.readableBytes());
                assertEquals(0, clientHandler.get().retransmissionQueue.size());
                assertEquals(0, clientHandler.get().receiveBuffer.readableBytes());
                Thread.sleep(1);
            }
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void openTimeout() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        // Do nothing. Your job not care about you. Your boss not care about you.
                        // Nobody giving a f*ck. Tomorrow you are getting hit by bus and dying,
                        // your job forgetting you and putting someone else to do for your job.
                        // Do nothing at every possible moment, but if you have to do something, do
                        // absolute minimum. But it is most preferable to do f*ck all. Sleep in.
                        // Take as many sick days as legally possible, give no f*cks. There is no
                        // greater purpose. No greater meaning. Nothing to be rushing for. We will
                        // all soon be dead. So just f*cking chill. When you learn to do nothing,
                        // then you'll find the real purpose of life: Do nothing.
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(Duration.ofSeconds(1), true));
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
            // wait for channel close due to handshake timeout
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            await().untilAsserted(() -> assertFalse(clientChannel.isOpen()));
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void closeTimeout() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        // server
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final LocalAddress serverAddress = new LocalAddress("ConnectionHandshakeHandlerIT");
        final Channel serverChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new SimpleChannelInboundHandler<ConnectionHandshakeSegment>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final ConnectionHandshakeSegment msg) {
                                if (msg.isFin()) {
                                    // drop
                                    ReferenceCountUtil.release(msg);
                                }
                                else {
                                    // pass through
                                    ctx.fireChannelRead(msg);
                                }
                            }
                        });
                        p.addLast(new ConnectionHandshakeHandler(Duration.ofSeconds(1), true));
                    }
                })
                .bind(serverAddress).sync().channel();

        // client
        final Channel clientChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new ConnectionHandshakeCodec());
                        p.addLast(new ConnectionHandshakeHandler(Duration.ofSeconds(1), true));
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
            // wait for handshake
            assertTrue(latch.await(5, TimeUnit.SECONDS));

            // wait for timeout
            assertThrows(ConnectionHandshakeException.class, clientChannel.close()::sync);
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
