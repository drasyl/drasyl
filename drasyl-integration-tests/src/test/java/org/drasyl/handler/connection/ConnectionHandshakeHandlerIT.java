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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import test.DropRandomOutboundMessagesHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TODO: Überlauf von SEG.NO behandeln
class ConnectionHandshakeHandlerIT {
    private static final float LOSS_RATE = 0.5f;
    private static final int MAX_DROP = 10;
    public static final int USER_TIMEOUT = 1_000;

    @Test
    void passiveOpenCompleted() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);

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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, false));
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
                        p.addLast(new DropRandomOutboundMessagesHandler(LOSS_RATE, MAX_DROP));
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
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
                        p.addLast(new DropRandomOutboundMessagesHandler(LOSS_RATE, MAX_DROP));
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
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
    void serverSwitchFromPassiveToActiveOpen() throws Exception {
        final CompletableFuture<Channel> future = new CompletableFuture<>();
        final CountDownLatch latch = new CountDownLatch(3);

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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, false));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.fireChannelActive();
                                future.complete(ch);
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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, false));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx,
                                                    Object msg) {
                                if (msg.equals("Hi")) {
                                    latch.countDown();
                                }
                                ctx.fireChannelRead(msg);
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
                .connect(serverAddress).sync().channel();

        try {
            final Channel serverSideChildChannel = future.get(USER_TIMEOUT, MILLISECONDS);

            // write should trigger active OPEN by server & write should be passed to channel afterwards
            serverSideChildChannel.writeAndFlush("Hi").sync();

            // handshake should be performed & message should be delivered
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        finally {
            clientChannel.close().sync();
            serverChannel.close().sync();
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void passiveServerHandshakeTimeout() throws Exception {
        final CompletableFuture<Channel> future = new CompletableFuture<>();

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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, false));
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.fireChannelActive();
                                future.complete(ch);
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
                        p.addLast(new SimpleChannelInboundHandler<>() {
                            @Override
                            protected void channelRead0(final ChannelHandlerContext ctx,
                                                        final Object msg) {
                                // let's drop all messages
                                ReferenceCountUtil.safeRelease(msg);
                            }
                        });
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
                    }
                })
                .connect(serverAddress).sync().channel();

        try {
            // the channel should be closed after timeout
            final Channel serverSideChildChannel = future.get(USER_TIMEOUT, MILLISECONDS);

            await().untilAsserted(() -> assertFalse(serverSideChildChannel.isOpen()));
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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
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
                        p.addLast(new ConnectionHandshakeHandler(USER_TIMEOUT, true));
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
