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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import test.DropMessagesHandler;
import test.DropMessagesHandler.DropRandomMessages;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.ChannelFutureListener.CLOSE;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Ansi.ansi;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReliableTransportHandlerIT {
    private static final Logger LOG = LoggerFactory.getLogger(ReliableTransportHandlerIT.class);
    private static final int MAX_DROP = 3;
    public static final Duration USER_TIMEOUT = ofSeconds(1);

    @BeforeEach
    void setup(final TestInfo info) {
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        LOG.debug(ansi().cyan().swap().format("# %-140s #", "STARTING " + info.getDisplayName()));
    }

    @AfterEach
    void cleanUp(final TestInfo info) {
        LOG.debug(ansi().cyan().swap().format("# %-140s #", "FINISHED " + info.getDisplayName()));
    }

    /**
     * Clients sends data to server. Correct receival is tested.
     */
    @ParameterizedTest
    @ValueSource(floats = { 0.0f })
    @Timeout(value = 5_000, unit = MILLISECONDS)
    void transmission(final float lossRate) throws Exception {
        final EventLoopGroup group = new DefaultEventLoopGroup();
        final ByteBuf receivedBuf = Unpooled.buffer();

        // Peer B
        final LocalAddress peerBAddress = new LocalAddress(StringUtil.simpleClassName(ReliableTransportHandlerIT.class));
        final ReliableTransportConfig peerBConfig = ReliableTransportConfig.newBuilder()
                .activeOpen(false)
                .rmem(32_000)
                .issSupplier(() -> 1_000_000L)
                .rto(ofMillis(100))
                .lBound(ofMillis(100))
                .userTimeout(USER_TIMEOUT)
                .build();
        final ReliableTransportHandler peerBHandler = new ReliableTransportHandler(peerBConfig);
        final Channel peerBServerChannel = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new SegmentCodec());
                        p.addLast(peerBHandler);
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
                                    p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                                }
                                else if (evt instanceof ConnectionClosing) {
                                    // confirm close request
                                    ctx.close();
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .bind(peerBAddress).sync().channel();

        // Peer A
        final ReliableTransportConfig peerAConfig = ReliableTransportConfig.newBuilder()
                .activeOpen(true)
                .rmem(32_000)
                .issSupplier(() -> 3_000_000L)
                .rto(ofMillis(100))
                .lBound(ofMillis(100))
                .userTimeout(USER_TIMEOUT)
                .build();
        final ReliableTransportHandler peerAHandler = new ReliableTransportHandler(peerAConfig);
        final Channel peerAChannel = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(final Channel ch) {
                        final ChannelPipeline p = ch.pipeline();
                        p.addLast(new SegmentCodec());
                        p.addLast(peerAHandler);
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void userEventTriggered(final ChannelHandlerContext ctx,
                                                           final Object evt) {
                                if (evt instanceof ConnectionHandshakeCompleted) {
                                    p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                                }
                                else if (evt instanceof ConnectionClosing) {
                                    // confirm close request
                                    ctx.close();
                                }
                                ctx.fireUserEventTriggered(evt);
                            }
                        });
                    }
                })
                .connect(peerBAddress).sync().channel();

        try {
            final int bytes = 5_000;
            final ByteBuf sentBuf = peerAChannel.alloc().buffer(bytes);
            sentBuf.writeBytes(randomBytes(bytes));

            final long startTime = System.nanoTime();
            LOG.debug(ansi().cyan().swap().format("# %-140s #", "Start transmission"));
            final ChannelFuture future = peerAChannel.writeAndFlush(sentBuf.copy()).addListener(CLOSE).syncUninterruptibly();
            LOG.debug(ansi().cyan().swap().format("# %-140s #", "Transmission done"));
            final long endTime = System.nanoTime();
            LOG.debug(ansi().cyan().swap().format("# %-140s #", "Transmitted " + bytes + " bytes within " + (endTime - startTime) / 1_000_000 + "ms."));

            assertTrue(future.isSuccess());
            assertEquals(sentBuf, receivedBuf);

            sentBuf.release();
            receivedBuf.release();
        }
        finally {
            group.shutdownGracefully().sync();
        }
    }

    // RFC 9293: 3.5. Establishing a Connection
    // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#section-3.5
    @Nested
    class EstablishingAConnection {
        // RFC 9293: Figure 6: Basic Three-Way Handshake for Connection Synchronization
        // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-6
        //     TCP Peer A                                           TCP Peer B
        //
        // 1.  CLOSED                                               LISTEN
        //
        // 2.  SYN-SENT    --> <SEQ=100><CTL=SYN>               --> SYN-RECEIVED
        //
        // 3.  ESTABLISHED <-- <SEQ=300><ACK=101><CTL=SYN,ACK>  <-- SYN-RECEIVED
        //
        // 4.  ESTABLISHED --> <SEQ=101><ACK=301><CTL=ACK>       --> ESTABLISHED
        //
        // 5.  ESTABLISHED --> <SEQ=101><ACK=301><CTL=ACK><DATA> --> ESTABLISHED
        @ParameterizedTest
        @ValueSource(floats = { 0.0f, 0.2f })
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void basicThreeWayHandshakeForConnectionSynchronization(final float lossRate) throws InterruptedException, ClosedChannelException {
            final Phaser phaser = new Phaser(2);
            final EventLoopGroup group = new DefaultEventLoopGroup();

            // TCP Peer B
            final LocalAddress peerBAddress = new LocalAddress(StringUtil.simpleClassName(ReliableTransportHandlerIT.class));
            final ReliableTransportConfig peerBConfig = ReliableTransportConfig.newBuilder()
                    .issSupplier(() -> 300L)
                    .activeOpen(false)
                    .rto(ofMillis(100))
                    .lBound(ofMillis(100))
                    .userTimeout(USER_TIMEOUT)
                    .build();
            final ReliableTransportHandler peerBHandler = new ReliableTransportHandler(peerBConfig);
            final Channel peerBServerChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new SegmentCodec());
                            p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                            p.addLast(peerBHandler);
                            p.addLast(new HandshakePhaser(phaser, "Peer B"));
                        }
                    })
                    .bind(peerBAddress).sync().channel();

            // TCP Peer A
            final ReliableTransportConfig peerAConfig = ReliableTransportConfig.newBuilder()
                    .issSupplier(() -> 100L)
                    .activeOpen(true)
                    .rto(ofMillis(100))
                    .lBound(ofMillis(100))
                    .userTimeout(USER_TIMEOUT)
                    .build();
            final ReliableTransportHandler peerAHandler = new ReliableTransportHandler(peerAConfig);
            final Channel peerAChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new SegmentCodec());
                            p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                            p.addLast(peerAHandler);
                            p.addLast(new HandshakePhaser(phaser, "Peer A"));
                        }
                    })
                    .connect(peerBAddress).sync().channel();

            try {
                phaser.arriveAndAwaitAdvance();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "Handshake on both peers completed"));
            }
            finally {
                group.shutdownGracefully().sync();
            }
        }

        // RFC 9293: Figure 7: Simultaneous Connection Synchronization
        // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-7
        //     TCP Peer A                                       TCP Peer B
        //
        // 1.  CLOSED                                           CLOSED
        //
        // 2.  SYN-SENT     --> <SEQ=100><CTL=SYN>              ...
        //
        // 3.  SYN-RECEIVED <-- <SEQ=300><CTL=SYN>              <-- SYN-SENT
        //
        // 4.               ... <SEQ=100><CTL=SYN>              --> SYN-RECEIVED
        //
        // 5.  SYN-RECEIVED --> <SEQ=100><ACK=301><CTL=SYN,ACK> ...
        //
        // 6.  ESTABLISHED  <-- <SEQ=300><ACK=101><CTL=SYN,ACK> <-- SYN-RECEIVED
        //
        // 7.               ... <SEQ=100><ACK=301><CTL=SYN,ACK> --> ESTABLISHED
        @ParameterizedTest
        @ValueSource(floats = { 0.0f, 0.2f })
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void simultaneousConnectionSynchronization(final float lossRate) throws InterruptedException, ClosedChannelException {
            final Phaser phaser = new Phaser(2);
            final EventLoopGroup group = new DefaultEventLoopGroup();

            // TCP Peer B
            final LocalAddress peerBAddress = new LocalAddress(StringUtil.simpleClassName(ReliableTransportHandlerIT.class));
            final ReliableTransportConfig peerBConfig = ReliableTransportConfig.newBuilder()
                    .issSupplier(() -> 300L)
                    .activeOpen(true)
                    .rto(ofMillis(100))
                    .lBound(ofMillis(100))
                    .userTimeout(USER_TIMEOUT)
                    .build();
            final ReliableTransportHandler peerBHandler = new ReliableTransportHandler(peerBConfig);
            final Channel peerBServerChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new SegmentCodec());
                            p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                            p.addLast(peerBHandler);
                            p.addLast(new HandshakePhaser(phaser, "Peer B"));
                        }
                    })
                    .bind(peerBAddress).sync().channel();

            // TCP Peer A
            final ReliableTransportConfig peerAConfig = ReliableTransportConfig.newBuilder()
                    .issSupplier(() -> 100L)
                    .activeOpen(true)
                    .rto(ofMillis(100))
                    .lBound(ofMillis(100))
                    .userTimeout(USER_TIMEOUT)
                    .build();
            final ReliableTransportHandler peerAHandler = new ReliableTransportHandler(peerAConfig);
            final Channel peerAChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new SegmentCodec());
                            p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                            p.addLast(peerAHandler);
                            p.addLast(new HandshakePhaser(phaser, "Peer A"));
                        }
                    })
                    .connect(peerBAddress).sync().channel();

            try {
                phaser.arriveAndAwaitAdvance();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "Handshake on both peers completed"));
            }
            finally {
                group.shutdownGracefully().sync();
            }
        }
    }

    // RFC 9293: 3.6. Closing a Connection
    // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#section-3.6
    @Nested
    class ClosingAConnection {
        // RFC 9293: Figure 12: Normal Close Sequence
        // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-12
        //     TCP Peer A                                           TCP Peer B
        //
        // 1.  ESTABLISHED                                          ESTABLISHED
        //
        // 2.  (Close)
        //     FIN-WAIT-1  --> <SEQ=100><ACK=300><CTL=FIN,ACK>  --> CLOSE-WAIT
        //
        // 3.  FIN-WAIT-2  <-- <SEQ=300><ACK=101><CTL=ACK>      <-- CLOSE-WAIT
        //
        // 4.                                                       (Close)
        //     TIME-WAIT   <-- <SEQ=300><ACK=101><CTL=FIN,ACK>  <-- LAST-ACK
        //
        // 5.  TIME-WAIT   --> <SEQ=101><ACK=301><CTL=ACK>      --> CLOSED
        //
        // 6.  (2 MSL)
        //     CLOSED
        @ParameterizedTest
        @ValueSource(floats = { 0.0f, 0.2f })
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void normalCloseSequence(final float lossRate) throws InterruptedException {
            final Phaser phaser = new Phaser(2);
            final EventLoopGroup group = new DefaultEventLoopGroup();
            final AtomicReference<Channel> peerBChannel = new AtomicReference<>();

            // TCP Peer B
            final LocalAddress peerBAddress = new LocalAddress(StringUtil.simpleClassName(ReliableTransportHandlerIT.class));
            final ReliableTransportConfig peerBConfig = ReliableTransportConfig.newBuilder()
                    .issSupplier(() -> 300L)
                    .activeOpen(false)
                    .rto(ofMillis(100))
                    .lBound(ofMillis(100))
                    .userTimeout(USER_TIMEOUT)
                    .build();
            final ReliableTransportHandler peerBHandler = new ReliableTransportHandler(peerBConfig);
            final Channel peerBServerChannel = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            peerBChannel.set(ch);
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new SegmentCodec());
                            p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                            p.addLast(peerBHandler);
                            p.addLast(new HandshakePhaser(phaser, "Peer B"));
                        }
                    })
                    .bind(peerBAddress).sync().channel();

            // TCP Peer A
            final ReliableTransportConfig peerAConfig = ReliableTransportConfig.newBuilder()
                    .issSupplier(() -> 100L)
                    .activeOpen(true)
                    .msl(ofMillis(100))
                    .rto(ofMillis(100))
                    .lBound(ofMillis(100))
                    .userTimeout(USER_TIMEOUT)
                    .build();
            final ReliableTransportHandler peerAHandler = new ReliableTransportHandler(peerAConfig);
            final Channel peerAChannel = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();
                            p.addLast(new SegmentCodec());
                            p.addLast(new DropMessagesHandler(new DropRandomMessages(lossRate, MAX_DROP), msg -> false));
                            p.addLast(peerAHandler);
                            p.addLast(new HandshakePhaser(phaser, "Peer A"));
                        }
                    })
                    .connect(peerBAddress).sync().channel();

            try {
                phaser.arriveAndAwaitAdvance();
                LOG.debug(ansi().cyan().swap().format("# %-140s #", "Handshake on both peers completed"));

                LOG.debug(ansi().cyan().swap().format("# %-140s #", "Peer A: Close connection"));
                assertTrue(peerAChannel.close().await().isSuccess());
                assertTrue(peerBChannel.get().closeFuture().await().isSuccess());
            }
            finally {
                peerBServerChannel.close().sync();
                group.shutdownGracefully().sync();
            }
        }
    }

    private static class HandshakePhaser extends ChannelDuplexHandler {
        private final Phaser phaser;
        private final String name;
        private boolean completed;

        public HandshakePhaser(final Phaser phaser, final String name) {
            this.phaser = requireNonNull(phaser);
            this.name = requireNonNull(name);
        }

        @Override
        public void close(final ChannelHandlerContext ctx,
                          final ChannelPromise promise) {
            if (completed) {
                completed = false;
                phaser.register();
            }
            ctx.close(promise);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) {
            if (evt instanceof ConnectionHandshakeCompleted) {
                LOG.debug(ansi().cyan().swap().format("# %-140s #", name + ": Handshake completed"));
                completed = true;
                phaser.arriveAndDeregister();
            }
            else if (evt instanceof ConnectionClosing) {
                LOG.debug(ansi().cyan().swap().format("# %-140s #", name + ": Confirm close request"));
                ctx.close();
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void handlerRemoved(final ChannelHandlerContext ctx) {
            if (completed) {
                completed = false;
                phaser.register();
            }
        }
    }
}
