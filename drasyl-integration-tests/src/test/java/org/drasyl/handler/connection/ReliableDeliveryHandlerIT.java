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
import io.netty.buffer.Unpooled;
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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static org.awaitility.Awaitility.await;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReliableDeliveryHandlerIT {
    private static final float LOSS_RATE = 0.2f;
    private static final int MAX_DROP = 3;

    /**
     * Server waits for handshake to be initiated by client.
     */
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
//                        p.addLast(new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                        p.addLast(new ReliableDeliveryHandler(Duration.ofHours(1), false, CLOSED, 1000, 64_000));
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
//                        p.addLast(new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                        p.addLast(new ReliableDeliveryHandler(Duration.ofHours(1), true, CLOSED, 1000, 32_000));
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

    /**
     * Both server and client initiate handshake simultaneous.
     */
    @Test
    void activeOpenCompleted() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

        // server
        final AtomicReference<ReliableDeliveryHandler> serverHandler = new AtomicReference<>();
        final AtomicReference<ReliableDeliveryHandler> clientHandler = new AtomicReference<>();
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
//                        p.addLast(new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                        serverHandler.set(new ReliableDeliveryHandler(Duration.ofHours(1), true));
                        p.addLast(serverHandler.get());
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx,
                                                    Object msg) throws Exception {
                                super.channelRead(ctx, msg);
                            }

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
//                        p.addLast(new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                        clientHandler.set(new ReliableDeliveryHandler(Duration.ofHours(1), true));
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
            assertTrue(latch.await(50000, TimeUnit.SECONDS));
        }
        finally {
            clientChannel.close();//.sync();
            serverChannel.close();//.sync();
            group.shutdownGracefully();//.sync();
        }
    }

    /**
     * Both server and client wait for handshake to be initiated by remote peer. Write of client
     * will implicitly initiate handshake.
     */
    @Test
    void passiveToActiveOpenCompleted() throws Exception {
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
//                        p.addLast(new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                        p.addLast(new ReliableDeliveryHandler(Duration.ofSeconds(1), false));
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
//                        p.addLast(new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                        p.addLast(new ReliableDeliveryHandler(Duration.ofSeconds(1), false));
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
            final ByteBuf buffer = Unpooled.buffer(10).writeBytes(RandomUtil.randomBytes(10));
            final ChannelFuture future = clientChannel.writeAndFlush(buffer).syncUninterruptibly();

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(future.isSuccess());
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    /**
     * Both server and client wait for handshake to be initiated by remote peer. Write of client
     * will implicitly initiate handshake, but server will not respond. This should cause a {@link
     * ConnectionHandshakeException}, failed write future, and followed by a closed channel.
     */
    @Test
    void passiveToActiveOpenCompletedTimeout() throws Exception {
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
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(final ChannelHandlerContext ctx,
                                                    final Object msg) {
                                ReferenceCountUtil.release(msg);
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
                        p.addLast(new ReliableDeliveryHandler(Duration.ofSeconds(1), false));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx,
                                                        Throwable cause) {
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
            final ByteBuf buffer = Unpooled.buffer(10).writeBytes(RandomUtil.randomBytes(10));
            final ChannelFuture channelFuture = clientChannel.writeAndFlush(buffer);

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(channelFuture.isSuccess());
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    /**
     * Client initiates connection establishment handshake, server is not responding. This should
     * cause a {@link ConnectionHandshakeException} followed by a closed channel.
     */
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
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(final ChannelHandlerContext ctx,
                                                    final Object msg) {
                                ReferenceCountUtil.release(msg);
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
                        p.addLast(new ReliableDeliveryHandler(Duration.ofSeconds(1), true));
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

    /**
     * Client initiates tear-down establishment handshake, Server is not responding. This should
     * cause a {@link ConnectionHandshakeException} followed by a closed channel.
     */
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
                        p.addLast(new SimpleChannelInboundHandler<Segment>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final Segment msg) {
                                if (!msg.isFin()) {
                                    // pass through
                                    ctx.fireChannelRead(msg.retain());
                                }
                            }
                        });
                        p.addLast(new ReliableDeliveryHandler(Duration.ofSeconds(1), true));
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
                        p.addLast(new ReliableDeliveryHandler(Duration.ofSeconds(1), true));
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

    /**
     * Clients sends data to server. Correct receival is tested.
     */
    @Test
    void transmission() throws Exception {
        final ByteBuf receivedBuf = Unpooled.buffer();

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
                        ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofHours(1), false, CLOSED, 1000, 32_000);
                        p.addLast(handler);
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(final ChannelHandlerContext ctx,
                                                    final Object msg) {
                                if (msg instanceof ByteBuf) {
                                    receivedBuf.writeBytes((ByteBuf) msg);
                                    ReferenceCountUtil.release(msg);
                                }
                                else {
                                    ctx.fireChannelRead(msg);
                                }
                            }

                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    // FIXME:
//                                    p.addAfter(p.context(ConnectionHandshakeCodec.class).name(), null, new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
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
                        p.addLast(new ReliableDeliveryHandler(Duration.ofHours(1), true, CLOSED, 1000, 64_000));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                // start dropping segments once handshake is completed
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    // FIXME:
//                                    p.addAfter(p.context(ConnectionHandshakeCodec.class).name(), null, new DropMessagesHandler(new DropNthMessage(2), msg -> false));
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            int bytes = 100_000;
            final ByteBuf sentBuf = clientChannel.alloc().buffer(bytes);
            sentBuf.writeBytes(randomBytes(bytes));

            final long startTime = System.nanoTime();
            final ChannelFuture future = clientChannel.writeAndFlush(sentBuf.copy()).syncUninterruptibly();
            final long endTime = System.nanoTime();
            System.err.println("Transmitted " + bytes + " bytes within " + (endTime - startTime) / 1_000_000 + "ms.");

            assertTrue(future.isSuccess());
            assertEquals(sentBuf, receivedBuf);

            sentBuf.release();
            receivedBuf.release();
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    /**
     * Clients sends data to server. Correct receival is tested.
     */
    @Test
    void completeTransmissionBeforeTeardown() throws Exception {
        final ByteBuf receivedBuf = Unpooled.buffer();

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
                        p.addLast(new ReliableDeliveryHandler(Duration.ofHours(1), false, CLOSED, 1000, 64_000));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(final ChannelHandlerContext ctx,
                                                    final Object msg) {
                                if (msg instanceof ByteBuf) {
                                    receivedBuf.writeBytes((ByteBuf) msg);
                                    ReferenceCountUtil.release(msg);
                                }
                                else {
                                    ctx.fireChannelRead(msg);
                                }
                            }

                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    // FIXME:
//                                    p.addAfter(p.context(ConnectionHandshakeCodec.class).name(), null, new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
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
                        p.addLast(new ReliableDeliveryHandler(Duration.ofHours(1), true, CLOSED, 1000, 64_000));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                // start dropping segments once handshake is completed
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    // FIXME:
//                                    p.addAfter(p.context(ConnectionHandshakeCodec.class).name(), null, new DropMessagesHandler(new DropRandomMessages(LOSS_RATE, MAX_DROP), msg -> false));
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            int bytes = 5_000;
            final ByteBuf sentBuf = clientChannel.alloc().buffer(bytes);
            sentBuf.writeBytes(randomBytes(bytes));

            clientChannel.writeAndFlush(sentBuf.copy()).addListener(CLOSE).syncUninterruptibly();

//            final long startTime = System.nanoTime();
//            final ChannelFuture channelFuture = clientChannel.writeAndFlush(sentBuf.copy()).addListener((ChannelFutureListener) future -> {
//                if (future.isSuccess()) {
//                    final long endTime = System.nanoTime();
//                    System.err.println("Transmitted " + bytes + " bytes within " + (endTime - startTime) / 1_000_000 + "ms.");
//                }
//                else {
//                    future.cause().printStackTrace();
//                }
//            });
//            clientChannel.pipeline().close();
//            channelFuture.syncUninterruptibly();

            //FIXME: add suport for cancel beim write. dann

            assertEquals(sentBuf, receivedBuf);

            sentBuf.release();
            receivedBuf.release();

            clientChannel.closeFuture().await();
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }
}
