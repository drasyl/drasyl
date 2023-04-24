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
package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.ConnectionConfig.Clock;
import org.drasyl.handler.connection.SegmentOption.TimestampsOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.stubbing.Answer;

import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.PSH;
import static org.drasyl.handler.connection.Segment.RST;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.SegmentMatchers.ack;
import static org.drasyl.handler.connection.SegmentMatchers.ctl;
import static org.drasyl.handler.connection.SegmentMatchers.mss;
import static org.drasyl.handler.connection.SegmentMatchers.seq;
import static org.drasyl.handler.connection.SegmentMatchers.tsOpt;
import static org.drasyl.handler.connection.SegmentMatchers.window;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.SegmentOption.TIMESTAMPS;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.handler.connection.State.CLOSE_WAIT;
import static org.drasyl.handler.connection.State.CLOSING;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.handler.connection.State.FIN_WAIT_1;
import static org.drasyl.handler.connection.State.FIN_WAIT_2;
import static org.drasyl.handler.connection.State.LAST_ACK;
import static org.drasyl.handler.connection.State.LISTEN;
import static org.drasyl.handler.connection.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.State.SYN_SENT;
import static org.drasyl.handler.connection.State.TIME_WAIT;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

@SuppressWarnings("NewClassNamingConvention")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class ConnectionHandlerTest {
    private static ByteBuf unpooledRandomBuffer(final int bytes) {
        return Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
    }

    private static class ChannelFutureAnswer implements Answer<ChannelFuture> {
        private final ChannelFuture future;

        public ChannelFutureAnswer(final ChannelFuture future) {
            this.future = requireNonNull(future);
        }

        @Override
        public ChannelFuture answer(final InvocationOnMock invocation) throws Throwable {
            invocation.getArgument(0, ChannelFutureListener.class).operationComplete(future);
            return future;
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
        @Nested
        class BasicThreeWayHandshakeForConnectionSynchronization {
            // TCP Peer A
            @Test
            void shouldConformWithBehaviorOfPeerA() {
                final ConnectionConfig config = ConnectionConfig.newBuilder()
                        .issSupplier(() -> 100)
                        .mmsS(1_266)
                        .rmem(5_000)
                        .build();
                final ConnectionHandler handler = new ConnectionHandler(config);
                final EmbeddedChannel channel = new EmbeddedChannel(handler);

                // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                assertThat(channel.readOutbound(), allOf(ctl(SYN), seq(100), window(5_000), mss(1_231)));
                assertEquals(SYN_SENT, handler.state);

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(0, handler.tcb.rcvNxt());

                // peer SYNchronizes his SEG with us and ACKed our segment, we reply with ACK for his SYN
                channel.writeInbound(new Segment(300, 101, (byte) (SYN | ACK), 64_000));
                assertThat(channel.readOutbound(), allOf(ctl(ACK), seq(101), ack(301), window(5_000)));
                assertEquals(ESTABLISHED, handler.state);

                assertEquals(101, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(301, handler.tcb.rcvNxt());

                assertTrue(channel.isOpen());
                channel.close();
            }

            // TCP Peer A
            @Test
            void shouldConformWithBehaviorOfPeerB() {
                final ConnectionConfig config = ConnectionConfig.newBuilder()
                        .issSupplier(() -> 300)
                        .activeOpen(false)
                        .build();
                final ConnectionHandler handler = new ConnectionHandler(config);
                final EmbeddedChannel channel = new EmbeddedChannel(handler);

                // handlerAdded on active channel should change state to LISTEN
                assertEquals(LISTEN, handler.state);

                // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
                channel.writeInbound(new Segment(100, SYN, 64_000));
                assertThat(channel.readOutbound(), allOf(ctl(SYN, ACK), seq(300), ack(101)));
                assertEquals(SYN_RECEIVED, handler.state);

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(301, handler.tcb.sndNxt());
                assertEquals(101, handler.tcb.rcvNxt());

                // peer ACKed our SYN
                // we piggyback some data, that should also be processed by the server
                final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(new Segment(101, 301, (byte) (PSH | ACK), data));
                assertEquals(ESTABLISHED, handler.state);

                assertEquals(301, handler.tcb.sndUna());
                assertEquals(301, handler.tcb.sndNxt());
                assertEquals(111, handler.tcb.rcvNxt());
                assertEquals(data, channel.readInbound());

                assertTrue(channel.isOpen());
                channel.close();
                data.release();
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
        @Nested
        class SimultaneousConnectionSynchronization {
            // TCP Peer A (and also same-behaving TCP Peer B)
            @Test
            void shouldConformWithBehaviorOfPeerA() {
                final ConnectionConfig config = ConnectionConfig.newBuilder()
                        .issSupplier(() -> 100)
                        .mmsS(1_266)
                        .mmsR(1_266)
                        .rmem(5_000)
                        .build();
                final ConnectionHandler handler = new ConnectionHandler(config);
                final EmbeddedChannel channel = new EmbeddedChannel(handler);

                // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                assertThat(channel.readOutbound(), allOf(ctl(SYN), seq(100), window(5_000), mss(1_241)));
                assertEquals(SYN_SENT, handler.state);

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(0, handler.tcb.rcvNxt());

                // peer SYNchronizes his SEG before our SYN has been received
                channel.writeInbound(new Segment(300, SYN, 64_000));
                assertEquals(SYN_RECEIVED, handler.state);
                assertThat(channel.readOutbound(), allOf(ctl(SYN, ACK), seq(100), ack(301), window(5_000)));

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(301, handler.tcb.rcvNxt());

                // peer respond to our SYN with ACK (and another SYN)
                channel.writeInbound(new Segment(301, 101, (byte) (SYN | ACK), 64_000));
                assertEquals(ESTABLISHED, handler.state);

                assertEquals(101, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(301, handler.tcb.rcvNxt());

                assertTrue(channel.isOpen());
                channel.close();
            }
        }

        // RFC 9293: 3.5.1. Half-Open Connections and Other Anomalies
        // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#section-3.5.1
        @Nested
        class HalfOpenConnectionsAndOtherAnomalies {
            // RFC 9293: Figure 9: Half-Open Connection Discovery
            // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-9
            //       TCP Peer A                                      TCP Peer B
            //
            //   1.  (REBOOT)                              (send 300,receive 100)
            //
            //   2.  CLOSED                                           ESTABLISHED
            //
            //   3.  SYN-SENT --> <SEQ=400><CTL=SYN>              --> (??)
            //
            //   4.  (!!)     <-- <SEQ=300><ACK=100><CTL=ACK>     <-- ESTABLISHED
            //
            //   5.  SYN-SENT --> <SEQ=100><CTL=RST>              --> (Abort!!)
            //
            //   6.  SYN-SENT                                         CLOSED
            //
            //   7.  SYN-SENT --> <SEQ=400><CTL=SYN>              -->
            @Nested
            class HalfOpenConnectionDiscovery {
                // TCP Peer A
                @Test
                void shouldConformWithBehaviorOfPeerA() {
                    final ConnectionConfig config = ConnectionConfig.newBuilder()
                            .issSupplier(() -> 400)
                            .build();
                    final ConnectionHandler handler = new ConnectionHandler(config);
                    final EmbeddedChannel channel = new EmbeddedChannel(handler);

                    // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                    assertThat(channel.readOutbound(), allOf(ctl(SYN), seq(400)));
                    assertEquals(SYN_SENT, handler.state);

                    assertEquals(400, handler.tcb.sndUna());
                    assertEquals(401, handler.tcb.sndNxt());
                    assertEquals(0, handler.tcb.rcvNxt());

                    // as we got an ACK for an unexpected seq, reset the peer
                    channel.writeInbound(new Segment(300, 100, ACK));
                    assertThat(channel.readOutbound(), allOf(ctl(RST), seq(100)));
                    assertEquals(SYN_SENT, handler.state);

                    assertEquals(400, handler.tcb.sndUna());
                    assertEquals(401, handler.tcb.sndNxt());
                    assertEquals(0, handler.tcb.rcvNxt());

                    assertTrue(channel.isOpen());
                    channel.close();
                }

                // TCP Peer B
                @Test
                void shouldConformWithBehaviorOfPeerB() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ConnectionConfig config = ConnectionConfig.DEFAULT;
                    final TransmissionControlBlock tcb = new TransmissionControlBlock(
                            config,
                            channel,
                            300L,
                            300L,
                            1220 * 64,
                            300L,
                            100L);
                    final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, null, null, null, channel.newPromise(), channel.newPromise(), null);
                    channel.pipeline().addLast(handler);

                    // other wants to SYNchronize with us, ACK with our expected seq
                    channel.writeInbound(new Segment(400, SYN, 64_000));
                    assertEquals(ESTABLISHED, handler.state);
                    assertThat(channel.readOutbound(), allOf(ctl(ACK), seq(300), ack(100)));

                    assertEquals(300, handler.tcb.sndUna());
                    assertEquals(300, handler.tcb.sndNxt());
                    assertEquals(100, handler.tcb.rcvNxt());

                    // as we sent an ACK for an unexpected seq, peer will reset us
                    final Segment msg = new Segment(100, RST);
                    assertThrows(ConnectionResetException.class, () -> channel.writeInbound(msg));
                    assertEquals(CLOSED, handler.state);
                    assertNull(handler.tcb);
                }
            }

            // RFC 9293: Figure 10: Active Side Causes Half-Open Connection Discovery
            // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-10
            //       TCP Peer A                                         TCP Peer B
            //
            // 1.  (REBOOT)                                  (send 300,receive 100)
            //
            // 2.  (??)    <-- <SEQ=300><ACK=100><DATA=10><CTL=ACK> <-- ESTABLISHED
            //
            // 3.          --> <SEQ=100><CTL=RST>                   --> (ABORT!!)
            @Nested
            class ActiveSideCausesHalfOpenConnectionDiscovery {
                // TCP Peer A
                @Test
                void shouldConformWithBehaviorOfPeerA() {
                    final ConnectionConfig config = ConnectionConfig.DEFAULT;
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, null, null, null, null, channel.newPromise(), channel.newPromise(), null);
                    channel.pipeline().addLast(handler);

                    final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                    final Segment seg = new Segment(300, 100, ACK, data);
                    channel.writeInbound(seg);
                    assertThat(channel.readOutbound(), allOf(ctl(RST), seq(100)));

                    channel.close();
                }
            }

            // RFC 9293: Figure 11: Old Duplicate SYN Initiates a Reset on Two Passive Sockets
            // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-11
            //     TCP Peer A                                    TCP Peer B
            //
            // 1.  LISTEN                                        LISTEN
            //
            // 2.       ... <SEQ=Z><CTL=SYN>                -->  SYN-RECEIVED
            //
            // 3.  (??) <-- <SEQ=X><ACK=Z+1><CTL=SYN,ACK>   <--  SYN-RECEIVED
            //
            // 4.       --> <SEQ=Z+1><CTL=RST>              -->  (return to LISTEN!)
            //
            // 5.  LISTEN                                        LISTEN
            @Nested
            class OldDuplicateSynInitiatesAResetOnTwoPassiveSockets {
                // TCP Peer B
                @Test
                void shouldConformWithBehaviorOfPeerB() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final long iss = 200;
                    final ConnectionConfig config = ConnectionConfig.newBuilder()
                            .activeOpen(false)
                            .issSupplier(() -> iss)
                            .mmsS(1_266)
                            .mmsR(1_266)
                            .build();
                    final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, new TransmissionControlBlock(config, iss, iss, 0, iss, 0, 0, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false), null, null, null, channel.newPromise(), channel.newPromise(), null);
                    channel.pipeline().addLast(handler);

                    // old duplicate ACK arrives at us
                    final long x = 200;
                    final long z = 100;
                    channel.writeInbound(new Segment(z, SYN));
                    assertThat(channel.readOutbound(), allOf(ctl(SYN, ACK), seq(x), ack(z + 1)));

                    // returned SYN/ACK causes a RST, we should return to LISTEN
                    channel.writeInbound(new Segment(z + 1, RST));
                    assertEquals(LISTEN, handler.state);

                    channel.close();
                }
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
        @Nested
        class NormalCloseSequence {
            // TCP Peer A
            @Test
            void shouldConformWithBehaviorOfPeerA() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionConfig config = ConnectionConfig.newBuilder()
                        .activeOpen(false)
                        .mmsS(1_266)
                        .mmsR(1_266)
                        .msl(ofMillis(100))
                        .build();
                final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, new TransmissionControlBlock(config, 100L, 100L, 1220 * 64, 100L, 300L, 300L, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false), null, null, null, channel.newPromise(), channel.newPromise(), null);
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.pipeline().close();
                assertEquals(FIN_WAIT_1, handler.state);
                assertThat(channel.readOutbound(), allOf(ctl(FIN, ACK), seq(100), ack(300)));
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // my close got ACKed
                channel.writeInbound(new Segment(300, 101, ACK));
                assertEquals(FIN_WAIT_2, handler.state);
                assertFalse(future.isDone());

                assertEquals(101, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // peer now triggers close as well
                channel.writeInbound(new Segment(300, 101, (byte) (FIN | ACK)));
                assertThat(channel.readOutbound(), allOf(ctl(ACK), seq(101), ack(301)));
                assertEquals(TIME_WAIT, handler.state);
                assertFalse(future.isDone());

                // wait 2 MSL
                await().untilAsserted(() -> {
                    channel.runScheduledPendingTasks();
                    assertEquals(CLOSED, handler.state);
                    assertTrue(future.isDone());
                });
            }

            // TCP Peer B
            @Test
            void shouldConformWithBehaviorOfPeerB() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionConfig config = ConnectionConfig.newBuilder()
                        .mmsS(1_266)
                        .mmsR(1_266)
                        .build();
                final TransmissionControlBlock tcb = new TransmissionControlBlock(config, 299L, 300L, 1220 * 64, 300L, 100L, 100L, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false);
                final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, null, null, null, channel.newPromise(), channel.newPromise(), null);
                channel.pipeline().addLast(handler);
                final ChannelHandlerContext ctx = channel.pipeline().context(handler);

                // peer triggers close
                channel.writeInbound(new Segment(100, 300, (byte) (FIN | ACK)));

                assertEquals(CLOSE_WAIT, handler.state);
                assertThat(channel.readOutbound(), allOf(ctl(ACK), seq(300), ack(101)));

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(300, handler.tcb.sndNxt());
                assertEquals(101, handler.tcb.rcvNxt());

                // local application has to close as well
                handler.close(ctx, ctx.newPromise());
                assertThat(channel.readOutbound(), allOf(ctl(FIN, ACK), seq(300), ack(101)));
                assertEquals(LAST_ACK, handler.state);

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(301, handler.tcb.sndNxt());
                assertEquals(101, handler.tcb.rcvNxt());

                // peer ACKed our close
                channel.writeInbound(new Segment(101, 301, ACK));

                assertEquals(CLOSED, handler.state);
                assertNull(handler.tcb);
            }
        }

        // RFC 9293: Figure 13: Simultaneous Close Sequence
        // RFC 9293: https://www.rfc-editor.org/rfc/rfc9293.html#figure-13
        //     TCP Peer A                                           TCP Peer B
        //
        // 1.  ESTABLISHED                                          ESTABLISHED
        //
        // 2.  (Close)                                              (Close)
        //     FIN-WAIT-1  --> <SEQ=100><ACK=300><CTL=FIN,ACK>  ... FIN-WAIT-1
        //                 <-- <SEQ=300><ACK=100><CTL=FIN,ACK>  <--
        //                 ... <SEQ=100><ACK=300><CTL=FIN,ACK>  -->
        //
        // 3.  CLOSING     --> <SEQ=101><ACK=301><CTL=ACK>      ... CLOSING
        //                 <-- <SEQ=301><ACK=101><CTL=ACK>      <--
        //                 ... <SEQ=101><ACK=301><CTL=ACK>      -->
        //
        // 4.  TIME-WAIT                                            TIME-WAIT
        //     (2 MSL)                                              (2 MSL)
        //     CLOSED                                               CLOSED
        @Nested
        class SimultaneousCloseSequence {
            // TCP Peer A (and also same-behaving TCP Peer B)
            @Test
            void shouldConformWithBehaviorOfPeerA() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionConfig config = ConnectionConfig.newBuilder()
                        .issSupplier(() -> 100L)
                        .activeOpen(false)
                        .mmsS(1_266)
                        .mmsR(1_266)
                        .msl(ofMillis(100))
                        .build();
                final TransmissionControlBlock tcb = new TransmissionControlBlock(config, 100L, 100L, 1220 * 64, 100L, 300L, 300L, new SendBuffer(channel), new RetransmissionQueue(), new ReceiveBuffer(channel), 0, 0, false);
                final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, null, null, null, channel.newPromise(), channel.newPromise(), null);
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.close();
                assertEquals(FIN_WAIT_1, handler.state);
                assertThat(channel.readOutbound(), allOf(ctl(FIN, ACK), seq(100), ack(300)));
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // got parallel close
                channel.writeInbound(new Segment(300, 100, (byte) (FIN | ACK)));
                assertEquals(CLOSING, handler.state);
                assertThat(channel.readOutbound(), allOf(ctl(ACK), seq(101), ack(301)));
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(301, handler.tcb.rcvNxt());

                channel.writeInbound(new Segment(301, 101, ACK));
                assertEquals(TIME_WAIT, handler.state);

                await().untilAsserted(() -> {
                    channel.runScheduledPendingTasks();
                    assertEquals(CLOSED, handler.state);
                });

                assertTrue(future.isDone());
                assertNull(handler.tcb);
            }
        }
    }

    // RFC 9293: 3.10. Event Processing
    // https://www.rfc-editor.org/rfc/rfc9293.html#name-event-processing
    @Nested
    class EventProcessing {
        @Mock(answer = RETURNS_DEEP_STUBS)
        ChannelHandlerContext ctx;
        @Mock(answer = RETURNS_DEEP_STUBS)
        ConnectionConfig config;
        @Mock(answer = RETURNS_DEEP_STUBS)
        TransmissionControlBlock tcb;
        @Mock(answer = RETURNS_DEEP_STUBS)
        ScheduledFuture<?> userTimer;
        @Mock(answer = RETURNS_DEEP_STUBS)
        ScheduledFuture<?> retransmissionTimer;
        @Mock(answer = RETURNS_DEEP_STUBS)
        ScheduledFuture<?> timeWaitTimer;
        @Mock(answer = RETURNS_DEEP_STUBS)
        ChannelPromise establishedPromise;
        @Mock(answer = RETURNS_DEEP_STUBS)
        ChannelPromise closedPromise;
        boolean pushSeen = true;
        @Captor
        ArgumentCaptor<Segment> segmentCaptor;
        @Mock(answer = RETURNS_DEEP_STUBS)
        Clock clock;

        @Nested
        class UserCall {
            // RFC 9293: 3.10.1.  OPEN Call
            // https://www.rfc-editor.org/rfc/rfc9293.html#name-open-call
            @Nested
            class UserCallOpen {
                @BeforeEach
                void setUp() {
                    when(ctx.executor().inEventLoop()).thenReturn(true);
                }

                @Nested
                class OnClosedState {
                    @Test
                    void withPassiveOpen() {
                        final ConnectionConfig config = ConnectionConfig.newBuilder()
                                .activeOpen(false)
                                .build();

                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.userCallOpen(ctx);

                        // RFC 9293: Create a new transmission control block (TCB) to hold connection state
                        // RFC 9293: information.
                        assertNotNull(handler.tcb);

                        // RFC 9293: If passive, enter the LISTEN state
                        assertEquals(LISTEN, handler.state);
                    }

                    @Test
                    void withActiveOpen() {
                        final long iss = 123L;
                        final long currentTime = 39L;
                        when(clock.time()).thenReturn(currentTime);
                        final ConnectionConfig.Builder builder = ConnectionConfig.newBuilder()
                                .activeOpen(true)
                                .issSupplier(() -> iss);
                        final ConnectionConfig config = builder.mmsS(1_266).mmsR(1_266)
                                .clock(clock)
                                .build();

                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.userCallOpen(ctx);

                        // RFC 9293: Create a new transmission control block (TCB) to hold connection state
                        // RFC 9293: information.
                        assertNotNull(handler.tcb);

                        // RFC 9293: if active and the remote socket is specified, issue a SYN segment.
                        // RFC 9293: An initial send sequence number (ISS) is selected.
                        assertEquals(iss, handler.tcb.iss());

                        // RFC 9293: A SYN segment of the form <SEQ=ISS><CTL=SYN> is sent.
                        // RFC 7323: Send a <SYN> segment of the form:
                        // RFC 7323: <SEQ=ISS><CTL=SYN><TSval=Snd.TSclock>
                        // RFC 9293: TCP implementations SHOULD send an MSS Option in every SYN segment
                        verify(ctx).write(segmentCaptor.capture(), any());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(iss), ctl(SYN), mss(1241), tsOpt(currentTime)));

                        // RFC 9293: Set SND.UNA to ISS, SND.NXT to ISS+1,
                        assertEquals(iss, handler.tcb.sndUna());
                        assertEquals(iss + 1, handler.tcb.sndNxt());

                        // RFC 9293: enter SYN-SENT state,
                        assertEquals(SYN_SENT, handler.state);
                    }
                }

                @Nested
                class OnListenState {
                    @Test
                    void shouldChangeFromPassiveToActive() {
                        final long iss = 123L;
                        final long currentTime = 39L;
                        when(clock.time()).thenReturn(currentTime);
                        final ConnectionConfig.Builder builder = ConnectionConfig.newBuilder()
                                .issSupplier(() -> iss);
                        final ConnectionConfig config = builder.mmsS(1_266).mmsR(1_266)
                                .clock(clock)
                                .build();
                        final TransmissionControlBlock tcb = new TransmissionControlBlock(config, ctx.channel(), 456L);

                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.userCallOpen(ctx);

                        // RFC 9293: select an ISS.
                        assertEquals(iss, handler.tcb.iss());

                        // RFC 9293: Send a SYN segment,
                        verify(ctx).write(segmentCaptor.capture(), any());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(iss), ctl(SYN), mss(1241), tsOpt(currentTime)));

                        // RFC 9293: set SND.UNA to ISS, SND.NXT to ISS+1.
                        assertEquals(iss, handler.tcb.sndUna());
                        assertEquals(iss + 1, handler.tcb.sndNxt());

                        // RFC 9293: Enter SYN-SENT state.
                        assertEquals(SYN_SENT, handler.state);
                    }
                }

                @Nested
                class OnAnyOtherState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "SYN_SENT",
                            "SYN_RECEIVED",
                            "ESTABLISHED",
                            "FIN_WAIT_1",
                            "FIN_WAIT_2",
                            "CLOSE_WAIT",
                            "CLOSING",
                            "LAST_ACK",
                            "TIME_WAIT"
                    })
                    void shouldThrowException(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        assertThrows(ConnectionAlreadyExistsException.class, () -> handler.userCallOpen(ctx));
                    }
                }
            }

            // RFC 9293: 3.10.2.  SEND Call
            // https://www.rfc-editor.org/rfc/rfc9293.html#name-send-call
            @Nested
            class UserCallSend {
                @Mock(answer = RETURNS_DEEP_STUBS)
                ByteBuf data;
                @Mock(answer = RETURNS_DEEP_STUBS)
                ChannelPromise promise;

                @Test
                void shouldRejectOutboundNonByteBufs() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ConnectionConfig.Builder builder = ConnectionConfig.newBuilder();
                    final ConnectionConfig config = builder.mmsS(1_266).mmsR(1_266)
                            .build();
                    final TransmissionControlBlock tcb = new TransmissionControlBlock(config, channel, 300L);
                    final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, null, null, null, channel.newPromise(), channel.newPromise(), null);
                    channel.pipeline().addLast(handler);

                    assertThrows(UnsupportedMessageTypeException.class, () -> channel.writeOutbound("Hello World"));

                    channel.close();
                }

                @Nested
                class OnClosedState {
                    @Test
                    void shouldFailPromise() {
                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.write(ctx, data, promise);

                        // RFC 9293: Otherwise, return "error: connection does not exist".
                        verify(promise).tryFailure(any(ConnectionDoesNotExistException.class));
                        verify(data).release();
                    }
                }

                @Nested
                class OnListenState {
                    @Test
                    void shouldChangeFromPassiveToActive(@Mock final SendBuffer sendBuffer) {
                        final long iss = 123L;
                        final long currentTime = 39L;
                        when(clock.time()).thenReturn(currentTime);
                        final ConnectionConfig.Builder builder = ConnectionConfig.newBuilder()
                                .issSupplier(() -> iss);
                        final ConnectionConfig config = builder.mmsS(1_266).mmsR(1_266)
                                .clock(clock)
                                .build();
                        final TransmissionControlBlock tcb = new TransmissionControlBlock(config, 0, 0, config.rmem(), 0, 456L, 456L, sendBuffer, new RetransmissionQueue(), new ReceiveBuffer(ctx.channel()), 0, 0, false);

                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);
                        when(ctx.handler()).thenReturn(handler);

                        handler.write(ctx, data, promise);

                        // RFC 9293: select an ISS.
                        assertEquals(iss, handler.tcb.iss());

                        // RFC 9293: Send a SYN segment,
                        // RFC 7323: Send a SYN segment containing the options: <TSval=Snd.TSclock>.
                        verify(ctx).write(segmentCaptor.capture(), any());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(iss), ctl(SYN), mss(1241), tsOpt(currentTime)));

                        // RFC 9293: set SND.UNA to ISS, SND.NXT to ISS+1.
                        assertEquals(iss, handler.tcb.sndUna());
                        assertEquals(iss + 1, handler.tcb.sndNxt());

                        // RFC 9293: Enter SYN-SENT state.
                        assertEquals(SYN_SENT, handler.state);

                        // RFC 9293: Data associated with SEND may be sent with SYN segment or queued for
                        // RFC 9293: transmission after entering ESTABLISHED state.
                        verify(sendBuffer).enqueue(data, promise);
                    }
                }

                @Nested
                class OnSynSentAndSynReceivedState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "SYN_SENT",
                            "SYN_RECEIVED"
                    })
                    void shouldQueueData(final State state, @Mock final SendBuffer sendBuffer) {
                        final ConnectionConfig config = ConnectionConfig.newBuilder().build();
                        final TransmissionControlBlock tcb = new TransmissionControlBlock(config, 0, 0, config.rmem(), 0, 456L, 456L, sendBuffer, new RetransmissionQueue(), new ReceiveBuffer(ctx.channel()), 0, 0, false);

                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.write(ctx, data, promise);

                        // RFC 9293: Queue the data for transmission after entering ESTABLISHED state.
                        verify(sendBuffer).enqueue(data, promise);
                    }
                }

                @Nested
                class OnEstablishedAndCloseWaitState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "ESTABLISHED",
                            "CLOSE_WAIT"
                    })
                    void shouldSegmentizeData(final State state) {
                        final long iss = 123L;
                        final long currentTime = 39L;
                        when(clock.time()).thenReturn(currentTime);
                        final ConnectionConfig.Builder builder = ConnectionConfig.newBuilder()
                                .issSupplier(() -> iss);
                        final ConnectionConfig config = builder.mmsS(1_266).mmsR(1_266)
                                .clock(clock)
                                .build();
                        final TransmissionControlBlock tcb = new TransmissionControlBlock(config, 201, 201, config.rmem(), 0, 456L, 456L, new SendBuffer(ctx.channel()), new RetransmissionQueue(), new ReceiveBuffer(ctx.channel()), 0, 0, true);

                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        final ByteBuf data = unpooledRandomBuffer(100);

                        handler.write(ctx, data, promise);

                        // RFC 9293: Segmentize the buffer and send it with a piggybacked acknowledgment
                        // RFC 9293: (acknowledgment value = RCV.NXT).
                        // RFC 7323: If the Snd.TS.OK flag is set, then include the TCP Timestamps option
                        // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in each data segment.
                        verify(ctx).write(segmentCaptor.capture(), any());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(201), ack(456), ctl(ACK), tsOpt(currentTime)));
                    }
                }

                @Nested
                class OnAnyOtherState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "FIN_WAIT_1",
                            "FIN_WAIT_2",
                            "CLOSING",
                            "LAST_ACK",
                            "TIME_WAIT"
                    })
                    void shouldFailPromise(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.write(ctx, data, promise);

                        // RFC 9293: Return "error: connection closing" and do not service request.
                        verify(promise).tryFailure(any(ConnectionClosingException.class));
                        verify(data).release();
                    }
                }
            }

            // RFC 9293: 3.10.3.  RECEIVE Call
            // https://www.rfc-editor.org/rfc/rfc9293.html#name-receive-call
            @Nested
            class UserCallReceive {
                @Nested
                class OnClosedState {
                    @Test
                    void shouldDoNothing() {
                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.read(ctx);

                        assertTrue(true);
                    }
                }

                @Nested
                class OnListenAndSynSentAndSynReceivedState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "LISTEN",
                            "SYN_SENT",
                            "SYN_RECEIVED"
                    })
                    void shouldQueueCallForProcessingAfterEnteringEstablishedState(final State state,
                                                                                   @Mock final ChannelPromise promise) {
                        when(promise.isSuccess()).thenReturn(true);

                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);
                        when(establishedPromise.addListener(any())).then(new Answer<ChannelFuture>() {
                            @Override
                            public ChannelFuture answer(final InvocationOnMock invocation) throws Throwable {
                                handler.state = ESTABLISHED;
                                invocation.getArgument(0, ChannelFutureListener.class).operationComplete(promise);
                                return promise;
                            }
                        });

                        handler.read(ctx);

                        // RFC 9293: Queue for processing after entering ESTABLISHED state.
                        verify(establishedPromise).addListener(any());

                        // this is done by the enqueued call
                        verify(tcb.receiveBuffer()).hasReadableBytes();
                    }
                }

                @Nested
                class OnEstablishedAndFinWait1AndFinWait2State {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "ESTABLISHED",
                            "FIN_WAIT_1",
                            "FIN_WAIT_2"
                    })
                    void shouldPassReceivedDataToUser(final State state) {
                        when(tcb.receiveBuffer().hasReadableBytes()).thenReturn(true);

                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.read(ctx);

                        // RFC 9293: Reassemble queued incoming segments into receive buffer and return
                        // RFC 9293: to user.
                        verify(tcb.receiveBuffer()).fireRead(ctx, tcb);
                    }
                }

                @Nested
                class OnCloseWaitState {
                    @Test
                    void shouldPassReceivedDataToUser() {
                        when(tcb.receiveBuffer().hasReadableBytes()).thenReturn(true);

                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSE_WAIT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.read(ctx);

                        // RFC 9293: Otherwise, any remaining data can be used to satisfy the
                        // RFC 9293: RECEIVE.
                        verify(tcb.receiveBuffer()).fireRead(ctx, tcb);
                    }
                }

                @Nested
                class OnClosingAndLastAckAndTimeWaitState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "CLOSING",
                            "LAST_ACK",
                            "TIME_WAIT"
                    })
                    void shouldDoNothing(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.read(ctx);

                        assertTrue(true);
                    }
                }
            }

            // RFC 9293: 3.10.4.  CLOSE Call
            // https://www.rfc-editor.org/rfc/rfc9293.html#name-close-call
            @Nested
            class UserCallClose {
                @Mock(answer = RETURNS_DEEP_STUBS)
                ChannelPromise promise;

                @Nested
                class OnClosedState {
                    @Test
                    void shouldConnectPromiseToClosedPromise() {
                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.close(ctx, promise);

                        verify(closedPromise).addListener(any());
                    }
                }

                @Nested
                class OnListenState {
                    @Test
                    void shouldCloseConnection() {
                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.close(ctx, promise);

                        // RFC 9293: Delete TCB,
                        verify(tcb).delete();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);

                        // connect promise to closedPromise
                        verify(closedPromise).addListener(any());
                    }
                }

                @Nested
                class OnSynSentState {
                    @Test
                    void shouldCloseConnection() {
                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.close(ctx, promise);

                        // RFC 9293: Any outstanding RECEIVEs are returned with "error: closing" responses.
                        verify(tcb.sendBuffer()).fail(any(ConnectionClosingException.class));

                        // RFC 9293: Delete TCB,
                        verify(tcb).delete();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);

                        // connect promise to closedPromise
                        verify(closedPromise).addListener(any());
                    }
                }

                @Nested
                class OnSynReceivedState {
                    @Test
                    void shouldCloseConnectionIfNoDataIsOutstanding() {
                        final SendBuffer sendBuffer = tcb.sendBuffer();
                        when(sendBuffer.isEmpty()).thenReturn(true);
                        when(tcb.sndNxt()).thenReturn(123L);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.close(ctx, promise);

                        // RFC 9293: If no SENDs have been issued and there is no pending data to send,
                        // RFC 9293: then form a FIN segment and send it,
                        verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(123), ctl(FIN, ACK)));

                        // RFC 9293: and enter FIN-WAIT-1 state;
                        assertEquals(FIN_WAIT_1, handler.state);

                        // connect promise to closedPromise
                        verify(closedPromise).addListener(any());
                    }

                    @Test
                    void shouldQueueCallForProcessingAfterEnteringEstablishedStateIfDataIsOutstanding() {
                        final SendBuffer sendBuffer = tcb.sendBuffer();
                        when(sendBuffer.isEmpty()).thenReturn(false).thenReturn(true);
                        when(ctx.newPromise()).thenReturn(promise);
                        when(promise.isSuccess()).thenReturn(true);
                        when(promise.addListener(any())).then(new ChannelFutureAnswer(promise));

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(establishedPromise.addListener(any())).then(new Answer<ChannelFuture>() {
                            @Override
                            public ChannelFuture answer(final InvocationOnMock invocation) throws Throwable {
                                handler.state = ESTABLISHED;
                                invocation.getArgument(0, ChannelFutureListener.class).operationComplete(promise);
                                return promise;
                            }
                        });

                        handler.close(ctx, UserCallClose.this.promise);

                        // RFC 9293: otherwise, queue for processing after entering ESTABLISHED state.
                        verify(establishedPromise).addListener(any());

                        // this is done by the enqueued call
                        assertEquals(FIN_WAIT_1, handler.state);
                    }
                }

                @Nested
                class OnEstablishedState {
                    @Test
                    void shouldQueueCallForProcessingAfterEverythingHasBeenSegmentized() {
                        when(tcb.sndNxt()).thenReturn(123L);
                        when(tcb.rcvNxt()).thenReturn(88L);
                        when(ctx.newPromise()).thenReturn(promise);
                        when(promise.isSuccess()).thenReturn(true);
                        when(promise.addListener(any())).then(new ChannelFutureAnswer(promise));

                        final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.close(ctx, promise);

                        // RFC 9293: Queue this until all preceding SENDs have been segmentized,
                        // (implictly tested by assertions below)

                        // RFC 9293: In any case, enter FIN-WAIT-1 state.
                        assertEquals(FIN_WAIT_1, handler.state);

                        // connect promise to closedPromise
                        verify(closedPromise).addListener(any());
                    }
                }

                @Nested
                class OnFinWait1AndFinWait2State {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "FIN_WAIT_1",
                            "FIN_WAIT_2"
                    })
                    void shouldFailPromise(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.close(ctx, promise);

                        // RFC 9293: Strictly speaking, this is an error and should receive an
                        // RFC 9293: "error: connection closing" response.
                        // RFC 9293: An "ok" response would be acceptable, too, as long as a second FIN is
                        // RFC 9293: not emitted (the first FIN may be retransmitted, though).
                        verify(promise).tryFailure(any(ConnectionClosingException.class));
                    }
                }

                @Nested
                class OnCloseWaitState {
                    @Test
                    void shouldQueueCallForProcessingAfterEverythingHasBeenSegmentized() {
                        when(tcb.sndNxt()).thenReturn(123L);
                        when(tcb.rcvNxt()).thenReturn(88L);
                        when(ctx.newPromise()).thenReturn(promise);
                        when(promise.isSuccess()).thenReturn(true);
                        when(promise.addListener(any())).then(new ChannelFutureAnswer(promise));

                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSE_WAIT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.close(ctx, UserCallClose.this.promise);

                        // RFC 9293: Queue this until all preceding SENDs have been segmentized,
                        // (implictly tested by assertions below)

                        // RFC 9293: then send a FIN segment,
                        verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(123L), ack(88L), ctl(FIN, ACK)));

                        // RFC 9293: enter LAST-ACK state.
                        assertEquals(LAST_ACK, handler.state);

                        // connect promise to closedPromise
                        verify(closedPromise).addListener(any());
                    }
                }

                @Nested
                class OnClosingAndLastAckAndTimeWaitState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "CLOSING",
                            "LAST_ACK",
                            "TIME_WAIT"
                    })
                    void shouldFailPromise(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.close(ctx, promise);

                        // RFC 9293: Strictly speaking, this is an error and should receive an
                        // RFC 9293: "error: connection closing" response.
                        // RFC 9293: An "ok" response would be acceptable, too, as long as a second FIN is
                        // RFC 9293: not emitted (the first FIN may be retransmitted, though).
                        verify(promise).tryFailure(any(ConnectionClosingException.class));
                    }
                }
            }

            // RFC 9293: 3.10.5.  ABORT Call
            // https://www.rfc-editor.org/rfc/rfc9293.html#name-abort-call
            @Nested
            class UserCallAbort {
                @BeforeEach
                void setUp() {
                    when(ctx.executor().inEventLoop()).thenReturn(true);
                }

                @Nested
                class OnClosedState {
                    @Test
                    void shouldThrowException() {
                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        assertThrows(ConnectionDoesNotExistException.class, () -> handler.userCallAbort());
                    }
                }

                @Nested
                class OnListenState {
                    @Test
                    void shouldCloseConnection() {
                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.userCallAbort();

                        // RFC 9293: Delete TCB,
                        verify(tcb).delete();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);
                    }
                }

                @Nested
                class OnSynSentState {
                    @Test
                    void shouldCloseConnection() {
                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.userCallAbort();

                        // RFC 9293: Any outstanding RECEIVEs should be returned with "error: connection
                        // RFC 9293: reset" responses.
                        verify(tcb.sendBuffer()).fail(any(ConnectionResetException.class));

                        // RFC 9293: Delete TCB,
                        verify(tcb).delete();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);
                    }
                }

                @Nested
                class OnSynReceivedAndEstablishedAndFinWait1AndFinWait2AndCloseWaitState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "SYN_RECEIVED",
                            "ESTABLISHED",
                            "FIN_WAIT_1",
                            "FIN_WAIT_2",
                            "CLOSE_WAIT"
                    })
                    void shouldCloseConnection(final State state) {
                        when(tcb.sndNxt()).thenReturn(123L);

                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.userCallAbort();

                        // RFC 9293: Send a reset segment:
                        // RFC 9293: <SEQ=SND.NXT><CTL=RST>
                        verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                        final Segment seg = segmentCaptor.getValue();
                        assertThat(seg, allOf(seq(123), ctl(RST)));

                        // RFC 9293: All queued SENDs and RECEIVEs should be given "connection reset"
                        // RFC 9293: notification;

                        // RFC 9293: all segments queued for transmission (except for the RST
                        // RFC 9293: formed above) or retransmission should be flushed.
                        verify(tcb.retransmissionQueue()).release();

                        // RFC 9293: Delete the TCB,
                        verify(tcb).delete();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);
                    }
                }

                @Nested
                class OnClosingAndLastAckAndTimeWaitState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "CLOSING",
                            "LAST_ACK",
                            "TIME_WAIT"
                    })
                    void shouldCloseConnection(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.userCallAbort();

                        // RFC 9293: Respond with "ok" and delete the TCB,
                        verify(tcb).delete();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);
                    }
                }
            }

            // RFC 9293: 3.10.6.  STATUS Call
            // https://www.rfc-editor.org/rfc/rfc9293.html#name-status-call
            @Nested
            class UserCallStatus {
                @Nested
                class OnClosedState {
                    @Test
                    void shouldThrowException() {
                        final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        assertThrows(ConnectionDoesNotExistException.class, () -> handler.userCallStatus());
                    }
                }

                @Nested
                class OnAnyOtherState {
                    @ParameterizedTest
                    @EnumSource(value = State.class, names = {
                            "LISTEN",
                            "SYN_SENT",
                            "SYN_RECEIVED",
                            "ESTABLISHED",
                            "FIN_WAIT_1",
                            "FIN_WAIT_2",
                            "CLOSE_WAIT",
                            "CLOSING",
                            "LAST_ACK",
                            "TIME_WAIT"
                    })
                    void shouldReturnStatus(final State state) {
                        final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        assertEquals(new ConnectionHandshakeStatus(state, tcb), handler.userCallStatus());
                    }
                }
            }
        }

        // RFC 9293: 3.10.7.  SEGMENT ARRIVES
        // https://www.rfc-editor.org/rfc/rfc9293.html#name-segment-arrives
        @Nested
        class SegmentArrives {
            @Mock(answer = RETURNS_DEEP_STUBS)
            Segment seg;

            @Nested
            class OnClosedState {
                @Test
                void shouldDiscardRst() {
                    when(seg.isRst()).thenReturn(true);

                    final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                    handler.channelRead(ctx, seg);
                    handler.channelReadComplete(ctx);

                    // RFC 9293: all data in the incoming segment is discarded.
                    // (this is handled by handler's auto release of all arrived segments)
                    verify(seg).release();
                }

                @Test
                void shouldResetAndAcknowledge() {
                    when(seg.isAck()).thenReturn(false);
                    when(seg.len()).thenReturn(100);

                    final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                    handler.channelRead(ctx, seg);
                    handler.channelReadComplete(ctx);

                    // RFC 9293: all data in the incoming segment is discarded.
                    // (this is handled by handler's auto release of all arrived segments)
                    verify(seg).release();

                    // RFC 9293: If the ACK bit is off, sequence number zero is used,
                    // RFC 9293: <SEQ=0><ACK=SEG.SEQ+SEG.LEN><CTL=RST,ACK>
                    verify(ctx).writeAndFlush(segmentCaptor.capture());
                    final Segment response = segmentCaptor.getValue();
                    assertThat(response, allOf(seq(0L), ack(100L), ctl(RST, ACK)));
                }

                @Test
                void shouldResetAcknowledgement() {
                    when(seg.isAck()).thenReturn(true);
                    when(seg.ack()).thenReturn(123L);

                    final ConnectionHandler handler = new ConnectionHandler(config, CLOSED, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                    handler.channelRead(ctx, seg);
                    handler.channelReadComplete(ctx);

                    // RFC 9293: all data in the incoming segment is discarded.
                    // (this is handled by handler's auto release of all arrived segments)
                    verify(seg).release();

                    // RFC 9293: If the ACK bit is off, sequence number zero is used,
                    // RFC 9293: <SEQ=0><ACK=SEG.SEQ+SEG.LEN><CTL=RST,ACK>
                    verify(ctx).writeAndFlush(segmentCaptor.capture());
                    final Segment response = segmentCaptor.getValue();
                    assertThat(response, allOf(seq(123L), ctl(RST)));
                }
            }

            @Nested
            class OnListenState {
                @Nested
                class CheckRstBit {
                    @Test
                    void shouldDiscard() {
                        when(seg.isRst()).thenReturn(true);

                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // should be released
                        verify(seg).release();
                    }
                }

                @Nested
                class CheckAckBit {
                    @Test
                    void shouldReset() {
                        when(seg.ack()).thenReturn(123L);
                        when(seg.isAck()).thenReturn(true);

                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, null, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: Any acknowledgment is bad if it arrives on a connection still in the LISTEN
                        // RFC 9293: state. An acceptable reset segment should be formed for any arriving
                        // RFC 9293: ACK-bearing segment. The RST should be formatted as follows:
                        // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
                        verify(ctx).writeAndFlush(segmentCaptor.capture());
                        final Segment response = segmentCaptor.getValue();
                        assertThat(response, allOf(seq(123L), ctl(RST)));

                        // should be released
                        verify(seg).release();
                    }
                }

                @Nested
                class CheckSynBit {
                    @Test
                    void shouldChangeToSynReceived() {
                        when(config.timestamps()).thenReturn(true);
                        when(config.issSupplier().getAsLong()).thenReturn(39L);
                        when(config.rmem()).thenReturn(64000);
                        when(config.mmsS()).thenReturn(1_266);
                        when(config.mmsR()).thenReturn(1_266);
                        when(config.rto()).thenReturn(ofMillis(1000));
                        when(config.clock().time()).thenReturn(3614L);
                        when(seg.seq()).thenReturn(123L);
                        when(seg.len()).thenReturn(1);
                        when(seg.isSyn()).thenReturn(true);
                        when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(4113L, 3604L));
                        when(seg.options().get(MAXIMUM_SEGMENT_SIZE)).thenReturn(1235);

                        final TransmissionControlBlock tcb = new TransmissionControlBlock(config, ctx.channel(), 0L);
                        final ConnectionHandler handler = new ConnectionHandler(config, LISTEN, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: Set RCV.NXT to SEG.SEQ+1, IRS is set to SEG.SEQ,
                        assertEquals(124L, tcb.rcvNxt());
                        assertEquals(123L, tcb.irs());

                        // RFC 9293: TCP endpoints MUST implement [...] receiving the MSS Option (MUST-14).
                        assertEquals(1235, tcb.sendMss());

                        // RFC 9293: ISS should be selected
                        assertEquals(39L, tcb.iss());

                        // RFC 9293: and a SYN segment sent of the form:
                        // RFC 9293: <SEQ=ISS><ACK=RCV.NXT><CTL=SYN,ACK>
                        // RFC 9293: TCP implementations SHOULD send an MSS Option in every SYN segment
                        // RFC 9293: when its receive MSS differs from the default 536 for IPv4 or 1220 for IPv6
                        // RFC 9293: (SHLD-5),
                        // RFC 9293: and MAY send it always (MAY-3).
                        // RFC 7323: If the Snd.TS.OK bit is on, include a TSopt
                        // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in this segment.
                        verify(ctx).write(segmentCaptor.capture(), any());
                        final Segment response = segmentCaptor.getValue();
                        assertThat(response, allOf(seq(39L), ack(124L), ctl(SYN, ACK), mss(1241), tsOpt(3614L, 4113L)));

                        // RFC 7323: Last.ACK.sent is set to RCV.NXT.
                        assertEquals(124L, tcb.lastAckSent());

                        // RFC 9293: SND.NXT is set to ISS+1 and SND.UNA to ISS.
                        assertEquals(40L, tcb.sndNxt());
                        assertEquals(39L, tcb.sndUna());

                        // RFC 9293: The connection state should be changed to SYN-RECEIVED.
                        assertEquals(SYN_RECEIVED, handler.state);
                    }
                }
            }

            @Nested
            class OnSynSentState {
                @Nested
                class CheckAckBit {
                    @BeforeEach
                    void setUp() {
                        when(seg.isAck()).thenReturn(true);
                    }

                    @Test
                    void shouldDiscardWhenSomethingNeverSentGotAckedAndSegmentIsReset() {
                        when(seg.ack()).thenReturn(123L);
                        when(seg.isRst()).thenReturn(true);
                        when(tcb.iss()).thenReturn(124L);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: If SEG.ACK =< ISS or SEG.ACK > SND.NXT, send a reset (unless the RST
                        // RFC 9293: bit is set, if so drop the segment and return)
                        verify(tcb, never()).send(any(), any());

                        // RFC 9293: and discard the segment.
                        verify(seg).release();
                    }

                    @Test
                    void shouldResetWhenSomethingNeverSentGotAckedAndSegmentIsNoReset() {
                        when(seg.ack()).thenReturn(123L);
                        when(seg.isRst()).thenReturn(false);
                        when(tcb.iss()).thenReturn(124L);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);
                        when(ctx.handler()).thenReturn(handler);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: If SEG.ACK =< ISS or SEG.ACK > SND.NXT, send a reset (unless the RST
                        // RFC 9293: bit is set, if so drop the segment and return)
                        verify(tcb).send(eq(ctx), segmentCaptor.capture());
                        final Segment response = segmentCaptor.getValue();
                        assertThat(response, allOf(seq(123L), ctl(RST)));

                        // RFC 9293: and discard the segment.
                        verify(seg).release();
                    }
                }

                @Nested
                class CheckRstBit {
                    @BeforeEach
                    void setUp() {
                        when(seg.isRst()).thenReturn(true);
                    }

                    @Test
                    void shouldDiscardIfPotentialBlindResetAttackHasBeenDetected() {
                        when(seg.seq()).thenReturn(123L);
                        when(tcb.rcvNxt()).thenReturn(456L);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: A potential blind reset attack is described in RFC 5961 [9]. The
                        // RFC 9293: mitigation described in that document has specific applicability explained
                        // RFC 9293: therein, and is not a substitute for cryptographic protection (e.g., IPsec
                        // RFC 9293: or TCP-AO). A TCP implementation that supports the mitigation described in
                        // RFC 9293: RFC 5961 SHOULD first check that the sequence number exactly matches
                        // RFC 9293: RCV.NXT prior to executing the action in the next paragraph.
                        verify(seg).release();
                    }

                    @Test
                    void shouldCloseConnectionIfAcknowledgementIsAcceptable() {
                        when(seg.isAck()).thenReturn(true);
                        when(seg.seq()).thenReturn(123L);
                        when(tcb.rcvNxt()).thenReturn(123L);
                        when(tcb.sndUna()).thenReturn(37L);
                        when(seg.ack()).thenReturn(38L);
                        when(tcb.sndNxt()).thenReturn(39L);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: If the ACK was acceptable,
                        // RFC 9293: then signal to the user "error: connection reset",
                        verify(ctx).fireExceptionCaught(any(ConnectionResetException.class));

                        // RFC 9293: drop the segment,
                        verify(seg).release();

                        // RFC 9293: enter CLOSED state,
                        assertEquals(CLOSED, handler.state);

                        // RFC 9293: delete TCB,
                        verify(tcb).delete();
                    }

                    @Test
                    void shouldDiscardIfSegmentIsNotAcceptable() {
                        when(seg.isAck()).thenReturn(false);
                        when(seg.seq()).thenReturn(123L);
                        when(tcb.rcvNxt()).thenReturn(123L);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: Otherwise (no ACK), drop the segment
                        verify(seg).release();
                    }
                }

                @Nested
                class CheckSynBit {
                    @BeforeEach
                    void setUp() {
                        when(seg.isSyn()).thenReturn(true);
                    }

                    @Test
                    void shouldEstablishConnectionWhenOurSynHasBeenAcked() {
                        when(config.timestamps()).thenReturn(true);
                        when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(214, 90));
                        when(seg.options().get(MAXIMUM_SEGMENT_SIZE)).thenReturn(1235);
                        when(tcb.tsRecent()).thenReturn(2L);
                        when(seg.isAck()).thenReturn(true);
                        when(seg.seq()).thenReturn(814L);
                        when(seg.len()).thenReturn(1);
                        when(tcb.sndUna()).thenReturn(122L);
                        when(seg.ack()).thenReturn(123L);
                        when(tcb.sndNxt()).thenReturn(124L);
                        when(config.clock().time()).thenReturn(111L);
                        when(tcb.sRtt()).thenReturn(21d);
                        when(config.clock().g()).thenReturn(0.001);
                        when(config.k()).thenReturn(4);
                        when(tcb.rcvNxt()).thenReturn(815L);
                        when(tcb.sndTsOk()).thenReturn(true);
                        when(tcb.config()).thenReturn(config);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: If the SYN bit is on and the security/compartment is acceptable,
                        // RFC 9293: then RCV.NXT is set to SEG.SEQ+1, IRS is set to SEG.SEQ.
                        verify(tcb).rcvNxt(815L);
                        verify(tcb).irs(814L);

                        // RFC 9293: SND.UNA should be advanced to equal SEG.ACK (if there is an ACK),
                        verify(tcb).sndUna(ctx, 123L);

                        // RFC 9293: and any segments on the retransmission queue that are thereby
                        // RFC 9293: acknowledged should be removed.
                        verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                        // RFC 7323: Check for a TSopt option;
                        // RFC 7323: if one is found, save SEG.TSval in variable TS.Recent
                        verify(tcb).tsRecent(214L);

                        // RFC 7323: and turn on the Snd.TS.OK bit in the connection control block.
                        verify(tcb).turnOnSndTsOk();

                        // RFC 7323: If the ACK bit is set, use Snd.TSclock - SEG.TSecr as the initial
                        // RFC 7323: RTT estimate.
                        // RFC 6298:       the host MUST set
                        // RFC 6298:       SRTT <- R
                        verify(tcb).sRtt(21);

                        // RFC 6298:       RTTVAR <- R/2
                        verify(tcb).rttVar(10.5);

                        // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                        verify(tcb).rto(21);

                        // RFC 9293: If SND.UNA > ISS (our SYN has been ACKed), change the connection state
                        // RFC 9293: to ESTABLISHED,
                        assertEquals(ESTABLISHED, handler.state);

                        // RFC 9293: TCP endpoints MUST implement [...] receiving the MSS Option (MUST-14).
                        verify(tcb).sendMss(1235);

                        // RFC 9293: form an ACK segment
                        // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                        // RFC 9293: and send it. Data or controls that were queued for transmission MAY be
                        // RFC 9293: included. Some TCP implementations suppress sending this segment when
                        // RFC 9293: the received segment contains data that will anyways generate an
                        // RFC 9293: acknowledgment in the later processing steps, saving this extra
                        // RFC 9293: acknowledgment of the SYN from being sent.
                        // RFC 7323: If the Snd.TS.OK bit is on, include a TSopt option
                        // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in this <ACK> segment.
                        verify(tcb.outgoingSegmentQueue()).place(eq(ctx), segmentCaptor.capture());
                        final Segment response = segmentCaptor.getValue();
                        assertThat(response, allOf(seq(124L), ack(815L), ctl(ACK), tsOpt(111, 2)));

                        // RFC 7323: Last.ACK.sent is set to RCV.NXT.
                        verify(tcb).lastAckSent(815L);

                        verify(seg).release();
                    }

                    @Test
                    void shouldChangeToSynReceivedWhenSynIsReceived() {
                        when(config.timestamps()).thenReturn(true);
                        when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(214, 90));
                        when(tcb.tsRecent()).thenReturn(2L);
                        when(seg.isAck()).thenReturn(true);
                        when(seg.seq()).thenReturn(814L);
                        when(seg.len()).thenReturn(1);
                        when(tcb.sndUna()).thenReturn(122L);
                        when(seg.ack()).thenReturn(123L);
                        when(tcb.sndNxt()).thenReturn(124L);
                        when(config.clock().time()).thenReturn(111L);
                        when(tcb.sRtt()).thenReturn(21d);
                        when(config.clock().g()).thenReturn(0.001);
                        when(config.k()).thenReturn(4);
                        when(tcb.rcvNxt()).thenReturn(815L);
                        when(tcb.sndTsOk()).thenReturn(true);
                        when(tcb.config()).thenReturn(config);
                        when(tcb.iss()).thenReturn(122L);
                        when(config.mmsR()).thenReturn(1_266);

                        final ConnectionHandler handler = new ConnectionHandler(config, SYN_SENT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                        handler.channelRead(ctx, seg);
                        handler.channelReadComplete(ctx);

                        // RFC 9293: If the SYN bit is on and the security/compartment is acceptable,
                        // RFC 9293: then RCV.NXT is set to SEG.SEQ+1, IRS is set to SEG.SEQ.
                        verify(tcb).rcvNxt(815L);
                        verify(tcb).irs(814L);

                        // RFC 9293: SND.UNA should be advanced to equal SEG.ACK (if there is an ACK),
                        verify(tcb).sndUna(ctx, 123L);

                        // RFC 9293: and any segments on the retransmission queue that are thereby
                        // RFC 9293: acknowledged should be removed.
                        verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                        // RFC 7323: Check for a TSopt option;
                        // RFC 7323: if one is found, save SEG.TSval in variable TS.Recent
                        verify(tcb).tsRecent(214L);

                        // RFC 7323: and turn on the Snd.TS.OK bit in the connection control block.
                        verify(tcb).turnOnSndTsOk();

                        // RFC 7323: If the ACK bit is set, use Snd.TSclock - SEG.TSecr as the initial
                        // RFC 7323: RTT estimate.
                        // RFC 6298:       the host MUST set
                        // RFC 6298:       SRTT <- R
                        verify(tcb).sRtt(21);

                        // RFC 6298:       RTTVAR <- R/2
                        verify(tcb).rttVar(10.5);

                        // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                        verify(tcb).rto(21);

                        // RFC 9293: Otherwise, enter SYN-RECEIVED,
                        assertEquals(SYN_RECEIVED, handler.state);

                        // RFC 9293: form a SYN,ACK segment
                        // RFC 9293: <SEQ=ISS><ACK=RCV.NXT><CTL=SYN,ACK>
                        // RFC 9293: and send it.
                        verify(tcb).send(eq(ctx), segmentCaptor.capture());
                        final Segment response = segmentCaptor.getValue();
                        assertThat(response, allOf(seq(122L), ack(815L), ctl(SYN, ACK), mss(1241), tsOpt(111, 2)));

                        // RFC 9293: Set the variables:
                        // RFC 9293: SND.WND <- SEG.WND
                        verify(tcb).sndWnd(seg.wnd());
                        // RFC 9293: SND.WL1 <- SEG.SEQ
                        verify(tcb).sndWl1(seg.seq());
                        // RFC 9293: SND.WL2 <- SEG.ACK
                        verify(tcb).sndWl2(seg.ack());

                        verify(seg).release();
                    }
                }
            }

            @Nested
            class OnAnyOtherState {
                @Nested
                class CheckSeq {
                    @Nested
                    class ForAnySynchronizedState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "SYN_RECEIVED",
                                "ESTABLISHED",
                                "FIN_WAIT_1",
                                "FIN_WAIT_2",
                                "CLOSE_WAIT",
                                "CLOSING",
                                "LAST_ACK",
                                "TIME_WAIT"
                        })
                        void shouldRejectSegmentAndSendAcknowledgementWithExpectedSeq(final State state) {
                            when(config.timestamps()).thenReturn(true);
                            when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(20, 30));
                            when(tcb.tsRecent()).thenReturn(25L);
                            when(seg.isRst()).thenReturn(false);
                            when(tcb.sndNxt()).thenReturn(122L);
                            when(tcb.rcvNxt()).thenReturn(815L);
                            when(tcb.sndTsOk()).thenReturn(true);
                            when(tcb.config()).thenReturn(config);
                            when(config.clock().time()).thenReturn(414L);
                            when(tcb.tsRecent()).thenReturn(99L);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 7323: If SEG.TSval < TS.Recent and the RST bit is off:
                            // RFC 7323: else the segment is not acceptable; follow the steps below
                            // RFC 7323: for an unacceptable segment.

                            // RFC 9293: If an incoming segment is not acceptable, an acknowledgment should
                            // RFC 9293: be sent in reply (unless the RST bit is set, if so drop the segment
                            // RFC 9293: and return):
                            // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                            // RFC 7323: If the Snd.TS.OK bit is on, include the Timestamps option
                            // RFC 7323: <TSval=Snd.TSclock,TSecr=TS.Recent> in this <ACK> segment.
                            verify(tcb).send(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(122L), ack(815L), ctl(ACK), tsOpt(414, 99)));

                            // RFC 7323: Last.ACK.sent is set to SEG.ACK of the acknowledgment.
                            verify(tcb).lastAckSent(815L);
                        }
                    }
                }

                @Nested
                class CheckRstBit {
                    @BeforeEach
                    void setUp() {
                        when(seg.isRst()).thenReturn(true);
                    }

                    @Nested
                    class BlindResetAttackDetection {
                        @Test
                        void shouldDiscardIfSeqIsOutsideReceiveWindow() {
                            when(tcb.rcvNxt()).thenReturn(122L);
                            when(seg.seq()).thenReturn(222L);
                            when(tcb.rcvWnd()).thenReturn(10L);

                            final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: 1)    If the RST bit is set and the sequence number is outside the current
                            // RFC 9293:       receive window, silently drop the segment.
                            verify(tcb, never()).send(any(), any());
                            verify(seg).release();
                        }

                        @Test
                        void shouldPassIfSeqMatchesExpectedNumber() {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);

                            final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: 2)    If the RST bit is set and the sequence number exactly matches the
                            // RFC 9293:       next expected sequence number (RCV.NXT), then TCP endpoints MUST
                            // RFC 9293:       reset the connection in the manner prescribed below according to the
                            // RFC 9293:        connection state.
                            assertEquals(CLOSED, handler.state);
                            verify(seg).release();
                        }

                        @Test
                        void shouldSendChallengeAckIfSeqIsWithinWindow() {
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvNxt()).thenReturn(122L);
                            when(seg.seq()).thenReturn(124L);
                            when(tcb.rcvWnd()).thenReturn(10L);

                            final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: 3)    If the RST bit is set and the sequence number does not exactly match
                            // RFC 9293:       the next expected sequence value, yet is within the current receive
                            // RFC 9293:       window, TCP endpoints MUST send an acknowledgment (challenge ACK):
                            // RFC 9293:       <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                            verify(tcb).send(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(122L), ctl(ACK)));

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class OnSynReceivedState {
                        @Test
                        void shouldChangeBackToListenStateIfPassiveOpenIsUsed() {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(config.activeOpen()).thenReturn(false);

                            final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If this connection was initiated with a passive OPEN (i.e.,
                            // RFC 9293: came from the LISTEN state), then return this connection to
                            // RFC 9293: LISTEN state
                            assertEquals(LISTEN, handler.state);

                            // RFC 9293: In either case, the retransmission queue should be flushed.
                            verify(tcb.retransmissionQueue()).release();

                            verify(seg).release();
                        }

                        @Test
                        void shouldCloseConnectionIfActiveOpenIsUsed() {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(config.activeOpen()).thenReturn(true);

                            final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If this connection was initiated with an active OPEN (i.e.,
                            // RFC 9293: came from SYN-SENT state), then the connection was refused;
                            // RFC 9293: signal the user "connection refused".
                            verify(ctx).fireExceptionCaught(any(ConnectionRefusedException.class));

                            // RFC 9293: In either case, the retransmission queue should be flushed.
                            verify(tcb.retransmissionQueue()).release();

                            // RFC 9293: And in the active OPEN case, enter the CLOSED state
                            assertEquals(CLOSED, handler.state);

                            verify(tcb).delete();

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class OnEstablishedAndFinWait1AndFinWait2AndCloseWaitState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "ESTABLISHED",
                                "FIN_WAIT_1",
                                "FIN_WAIT_2",
                                "CLOSE_WAIT"
                        })
                        void shouldCloseConnection(final State state) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the RST bit is set, then any outstanding RECEIVEs and SEND
                            // RFC 9293: should receive "reset" responses.
                            verify(tcb.sendBuffer()).fail(any(ConnectionResetException.class));

                            // RFC 9293: All segment queues should be flushed.
                            verify(tcb.retransmissionQueue()).release();

                            // RFC 9293: Users should also receive an unsolicited general
                            // RFC 9293: "connection reset" signal.
                            verify(ctx).fireExceptionCaught(any(ConnectionResetException.class));

                            // RFC 9293: Enter the CLOSED state, delete the TCB
                            assertEquals(CLOSED, handler.state);

                            // RFC 9293: delete the TCB,
                            verify(tcb).delete();

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class OnClosingAndLastAckAndTimeWaitState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "CLOSING",
                                "LAST_ACK",
                                "TIME_WAIT"
                        })
                        void shouldCloseConnection(final State state) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the RST bit is set, then enter the CLOSED state,
                            assertEquals(CLOSED, handler.state);

                            // RFC 9293: delete the TCB,
                            verify(tcb).delete();

                            verify(seg).release();
                        }
                    }
                }

                @Nested
                class CheckSynBit {
                    @BeforeEach
                    void setUp() {
                        when(seg.isSyn()).thenReturn(true);
                    }

                    @Nested
                    class OnSynReceivedState {
                        @Test
                        void shouldChangeToListenState() {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(config.activeOpen()).thenReturn(false);

                            final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the connection was initiated with a passive OPEN, then
                            // RFC 9293: return this connection to the LISTEN state and return.
                            assertEquals(LISTEN, handler.state);

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class ForAnySynchronizedState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "ESTABLISHED",
                                "FIN_WAIT_1",
                                "FIN_WAIT_2",
                                "CLOSE_WAIT",
                                "CLOSING",
                                "LAST_ACK",
                                "TIME_WAIT"
                        })
                        void shouldSendChallengeAck(final State state) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(tcb.sndNxt()).thenReturn(88L);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the SYN bit is set in these synchronized states, it may be
                            // RFC 9293: either a legitimate new connection attempt (e.g., in the case
                            // RFC 9293: of TIME-WAIT), an error where the connection should be reset,
                            // RFC 9293: or the result of an attack attempt, as described in
                            // RFC 9293: RFC 5961 [9]. For the TIME-WAIT state, new connections can be
                            // RFC 9293: accepted if the Timestamp Option is used and meets expectations
                            // RFC 9293: (per [40]). For all other cases, RFC 5961 provides a mitigation
                            // RFC 9293: with applicability to some situations, though there are also
                            // RFC 9293: alternatives that offer cryptographic protection (see
                            // RFC 9293: Section 7). RFC 5961 recommends that in these synchronized
                            // RFC 9293: states, if the SYN bit is set, irrespective of the sequence
                            // RFC 9293: number, TCP endpoints MUST send a "challenge ACK" to the remote
                            // RFC 9293: peer:
                            // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                            verify(tcb).send(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            verify(seg).release();
                        }
                    }
                }

                @Nested
                class CheckAckBit {
                    @Nested
                    class AckBitOff {
                        @BeforeEach
                        void setUp() {
                            when(seg.isAck()).thenReturn(false);
                        }

                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "SYN_RECEIVED",
                                "ESTABLISHED",
                                "FIN_WAIT_1",
                                "FIN_WAIT_2",
                                "CLOSE_WAIT",
                                "CLOSING",
                                "LAST_ACK",
                                "TIME_WAIT"
                        })
                        void shouldDiscardSegment(final State state) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(tcb.rcvWnd()).thenReturn(10L);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: if the ACK bit is off,
                            // RFC 9293: drop the segment
                            verify(tcb, never()).send(any(), any());

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class AckBitOn {
                        @BeforeEach
                        void setUp() {
                            when(seg.isAck()).thenReturn(true);
                        }

                        @Nested
                        class BlindDataInjectionAttackDetection {
                            @Test
                            void shouldDiscardSegAndSendAck() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(89L);
                                when(tcb.rcvWnd()).thenReturn(10L);
                                when(tcb.sndNxt()).thenReturn(88L);

                                final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: RFC 5961 [9], Section 5 describes a potential blind data injection attack,
                                // RFC 9293: and mitigation that implementations MAY choose to include (MAY-12). TCP
                                // RFC 9293: stacks that implement RFC 5961 MUST add an input check that the ACK value
                                // RFC 9293: is acceptable only if it is in the range of
                                // RFC 9293: ((SND.UNA - MAX.SND.WND) =< SEG.ACK =< SND.NXT).

                                // RFC 9293: All incoming segments whose ACK value doesn't satisfy the above
                                // RFC 9293: condition MUST be discarded
                                verify(seg).release();

                                // RFC 9293: and an ACK sent back.
                                verify(tcb).send(eq(ctx), segmentCaptor.capture());
                                final Segment response = segmentCaptor.getValue();
                                assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));
                            }
                        }

                        @Nested
                        class OnSynReceivedState {
                            @Test
                            void shouldEstablishConnectionWhenAckIsAcceptable() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(88L);
                                when(tcb.rcvWnd()).thenReturn(10L);

                                final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then enter ESTABLISHED state
                                assertEquals(ESTABLISHED, handler.state);

                                // RFC 9293: and continue processing with the variables below set to:
                                // RFC 9293: SND.WND <- SEG.WND
                                verify(tcb, times(2)).sndWnd(seg.wnd());
                                // RFC 9293: SND.WL1 <- SEG.SEQ
                                verify(tcb, times(2)).sndWl1(seg.seq());
                                // RFC 9293: SND.WL2 <- SEG.ACK
                                verify(tcb, times(2)).sndWl2(seg.ack());

                                verify(seg).release();
                            }

                            @Test
                            void shouldResetIfAckIsNotAcceptable() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(88L);
                                when(tcb.sndNxt()).thenReturn(88L);
                                when(tcb.rcvWnd()).thenReturn(10L);

                                final ConnectionHandler handler = new ConnectionHandler(config, SYN_RECEIVED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If the segment acknowledgment is not acceptable, form a
                                // RFC 9293: reset segment
                                // RFC 9293: <SEQ=SEG.ACK><CTL=RST>
                                // RFC 9293: and send it.
                                verify(tcb).send(eq(ctx), segmentCaptor.capture());
                                final Segment response = segmentCaptor.getValue();
                                assertThat(response, allOf(seq(88L), ctl(RST)));

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnEstablishedState {
                            @Test
                            void shouldAcknowledgeSeg() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(89L);
                                when(tcb.rcvWnd()).thenReturn(10L);
                                when(config.timestamps()).thenReturn(true);
                                when(config.clock().time()).thenReturn(3614L);
                                when(config.alpha()).thenReturn(1d / 8);
                                when(config.beta()).thenReturn(1d / 4);
                                when(config.k()).thenReturn(4);
                                when(tcb.smss()).thenReturn(1000L);
                                when(tcb.sndTsOk()).thenReturn(true);
                                when(tcb.flightSize()).thenReturn(64_000L);
                                when(tcb.sRtt()).thenReturn(21d);
                                when(tcb.rttVar()).thenReturn(2.4);
                                when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(4113L, 3604L));

                                final ConnectionHandler handler = new ConnectionHandler(config, ESTABLISHED, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
                                verify(tcb).sndUna(ctx, 88L);

                                // RFC 9293: Any segments on the retransmission queue that are thereby entirely
                                // RFC 9293: acknowledged are removed.
                                // RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
                                // RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
                                // RFC 9293: response).
                                verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                                // RFC 7323: Also compute a new estimate of round-trip time.
                                // RFC 7323: If Snd.TS.OK bit is on, use Snd.TSclock - SEG.TSecr;
                                // RFC 6298: (2.3) When a subsequent RTT measurement R' is made,
                                // RFC 7323: RTTVAR <- (1 - beta') * RTTVAR + beta' * |SRTT - R'|
                                // RFC 7323: SRTT <- (1 - alpha') * SRTT + alpha' * R'
                                // RFC 6298:       After the computation, a host MUST update
                                // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                                verify(tcb).rttVar(2.4671875d);
                                verify(tcb).sRtt(20.95703125d);
                                verify(tcb).rto(31);

                                // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
                                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                                // RFC 9293: set SND.WND <- SEG.WND,
                                verify(tcb).sndWnd(seg.wnd());
                                // RFC 9293: set SND.WL1 <- SEG.SEQ,
                                verify(tcb).sndWl1(seg.seq());
                                // RFC 9293: and set SND.WL2 <- SEG.ACK.
                                verify(tcb).sndWl2(seg.ack());

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnFinWait1State {
                            @Test
                            void shouldChangeToFinWait2State() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(89L);
                                when(tcb.rcvWnd()).thenReturn(10L);
                                when(config.timestamps()).thenReturn(true);
                                when(config.clock().time()).thenReturn(3614L);
                                when(config.alpha()).thenReturn(1d / 8);
                                when(config.beta()).thenReturn(1d / 4);
                                when(config.k()).thenReturn(4);
                                when(tcb.smss()).thenReturn(1000L);
                                when(tcb.sndTsOk()).thenReturn(true);
                                when(tcb.flightSize()).thenReturn(64_000L);
                                when(tcb.sRtt()).thenReturn(21d);
                                when(tcb.rttVar()).thenReturn(2.4);
                                when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(4113L, 3604L));

                                final ConnectionHandler handler = new ConnectionHandler(config, FIN_WAIT_1, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
                                verify(tcb).sndUna(ctx, 88L);

                                // RFC 9293: Any segments on the retransmission queue that are thereby entirely
                                // RFC 9293: acknowledged are removed.
                                // RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
                                // RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
                                // RFC 9293: response).
                                verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                                // RFC 7323: Also compute a new estimate of round-trip time.
                                // RFC 7323: If Snd.TS.OK bit is on, use Snd.TSclock - SEG.TSecr;
                                // RFC 6298: (2.3) When a subsequent RTT measurement R' is made,
                                // RFC 7323: RTTVAR <- (1 - beta') * RTTVAR + beta' * |SRTT - R'|
                                // RFC 7323: SRTT <- (1 - alpha') * SRTT + alpha' * R'
                                // RFC 6298:       After the computation, a host MUST update
                                // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                                verify(tcb).rttVar(2.4671875d);
                                verify(tcb).sRtt(20.95703125d);
                                verify(tcb).rto(31);

                                // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
                                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                                // RFC 9293: set SND.WND <- SEG.WND,
                                verify(tcb).sndWnd(seg.wnd());
                                // RFC 9293: set SND.WL1 <- SEG.SEQ,
                                verify(tcb).sndWl1(seg.seq());
                                // RFC 9293: and set SND.WL2 <- SEG.ACK.
                                verify(tcb).sndWl2(seg.ack());

                                // RFC 9293: if the FIN segment is now acknowledged, then enter FIN-WAIT-2
                                // RFC 9293: and continue processing in that state.
                                assertEquals(FIN_WAIT_2, handler.state);

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnFinWait2State {
                            @Test
                            void shouldChangeToListenState() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(89L);
                                when(tcb.rcvWnd()).thenReturn(10L);

                                final ConnectionHandler handler = new ConnectionHandler(config, FIN_WAIT_2, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
                                verify(tcb).sndUna(ctx, 88L);

                                // RFC 9293: Any segments on the retransmission queue that are thereby entirely
                                // RFC 9293: acknowledged are removed.
                                // RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
                                // RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
                                // RFC 9293: response).
                                verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                                // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
                                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                                // RFC 9293: set SND.WND <- SEG.WND,
                                verify(tcb).sndWnd(seg.wnd());
                                // RFC 9293: set SND.WL1 <- SEG.SEQ,
                                verify(tcb).sndWl1(seg.seq());
                                // RFC 9293: and set SND.WL2 <- SEG.ACK.
                                verify(tcb).sndWl2(seg.ack());

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnCloseWaitState {
                            @Test
                            void shouldRemoveAcknowledgedSegments() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(89L);
                                when(tcb.rcvWnd()).thenReturn(10L);
                                when(config.timestamps()).thenReturn(true);
                                when(config.clock().time()).thenReturn(3614L);
                                when(config.alpha()).thenReturn(1d / 8);
                                when(config.beta()).thenReturn(1d / 4);
                                when(config.k()).thenReturn(4);
                                when(tcb.smss()).thenReturn(1000L);
                                when(tcb.sndTsOk()).thenReturn(true);
                                when(tcb.flightSize()).thenReturn(64_000L);
                                when(tcb.sRtt()).thenReturn(21d);
                                when(tcb.rttVar()).thenReturn(2.4);
                                when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(4113L, 3604L));

                                final ConnectionHandler handler = new ConnectionHandler(config, CLOSE_WAIT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
                                verify(tcb).sndUna(ctx, 88L);

                                // RFC 9293: Any segments on the retransmission queue that are thereby entirely
                                // RFC 9293: acknowledged are removed.
                                // RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
                                // RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
                                // RFC 9293: response).
                                verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                                // RFC 7323: Also compute a new estimate of round-trip time.
                                // RFC 7323: If Snd.TS.OK bit is on, use Snd.TSclock - SEG.TSecr;
                                // RFC 6298: (2.3) When a subsequent RTT measurement R' is made,
                                // RFC 7323: RTTVAR <- (1 - beta') * RTTVAR + beta' * |SRTT - R'|
                                // RFC 7323: SRTT <- (1 - alpha') * SRTT + alpha' * R'
                                // RFC 6298:       After the computation, a host MUST update
                                // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                                verify(tcb).rttVar(2.4671875d);
                                verify(tcb).sRtt(20.95703125d);
                                verify(tcb).rto(31);

                                // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
                                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                                // RFC 9293: set SND.WND <- SEG.WND,
                                verify(tcb).sndWnd(seg.wnd());
                                // RFC 9293: set SND.WL1 <- SEG.SEQ,
                                verify(tcb).sndWl1(seg.seq());
                                // RFC 9293: and set SND.WL2 <- SEG.ACK.
                                verify(tcb).sndWl2(seg.ack());

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnClosingState {
                            @Test
                            void shouldChangeToTimeWait() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(89L);
                                when(tcb.rcvWnd()).thenReturn(10L);
                                when(config.timestamps()).thenReturn(true);
                                when(config.clock().time()).thenReturn(3614L);
                                when(config.alpha()).thenReturn(1d / 8);
                                when(config.beta()).thenReturn(1d / 4);
                                when(config.k()).thenReturn(4);
                                when(tcb.smss()).thenReturn(1000L);
                                when(tcb.sndTsOk()).thenReturn(true);
                                when(tcb.flightSize()).thenReturn(64_000L);
                                when(tcb.sRtt()).thenReturn(21d);
                                when(tcb.rttVar()).thenReturn(2.4);
                                when(seg.options().get(TIMESTAMPS)).thenReturn(new TimestampsOption(4113L, 3604L));

                                final ConnectionHandler handler = new ConnectionHandler(config, CLOSING, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: If SND.UNA < SEG.ACK =< SND.NXT, then set SND.UNA <- SEG.ACK.
                                verify(tcb).sndUna(ctx, 88L);

                                // RFC 9293: Any segments on the retransmission queue that are thereby entirely
                                // RFC 9293: acknowledged are removed.
                                // RFC 9293: Users should receive positive acknowledgments for buffers that have been SENT
                                // RFC 9293: and fully acknowledged (i.e., SEND buffer should be returned with "ok"
                                // RFC 9293: response).
                                verify(tcb.retransmissionQueue()).removeAcknowledged(any(), any());

                                // RFC 7323: Also compute a new estimate of round-trip time.
                                // RFC 7323: If Snd.TS.OK bit is on, use Snd.TSclock - SEG.TSecr;
                                // RFC 6298: (2.3) When a subsequent RTT measurement R' is made,
                                // RFC 7323: RTTVAR <- (1 - beta') * RTTVAR + beta' * |SRTT - R'|
                                // RFC 7323: SRTT <- (1 - alpha') * SRTT + alpha' * R'
                                // RFC 6298:       After the computation, a host MUST update
                                // RFC 6298:       RTO <- SRTT + max (G, K*RTTVAR)
                                verify(tcb).rttVar(2.4671875d);
                                verify(tcb).sRtt(20.95703125d);
                                verify(tcb).rto(31);

                                // RFC 9293: If SND.UNA =< SEG.ACK =< SND.NXT, the send window should be updated.
                                // RFC 9293: If (SND.WL1 < SEG.SEQ or (SND.WL1 = SEG.SEQ and SND.WL2 =< SEG.ACK)),
                                // RFC 9293: set SND.WND <- SEG.WND,
                                verify(tcb).sndWnd(seg.wnd());
                                // RFC 9293: set SND.WL1 <- SEG.SEQ,
                                verify(tcb).sndWl1(seg.seq());
                                // RFC 9293: and set SND.WL2 <- SEG.ACK.
                                verify(tcb).sndWl2(seg.ack());

                                // RFC 9293: if the ACK acknowledges our FIN, then enter the TIME-WAIT
                                // RFC 9293: state;
                                assertEquals(TIME_WAIT, handler.state);

                                // RFC 9293: start the time-wait timer
                                verify(timeWaitTimer).cancel(false);
                                assertNotNull(handler.timeWaitTimer);
                                assertNotSame(timeWaitTimer, handler.timeWaitTimer);

                                // RFC 9293: turn off the other timers
                                userTimer.cancel(false);
                                retransmissionTimer.cancel(false);

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnLastAckState {
                            @Test
                            void shouldCloseConnection() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(88L);
                                when(tcb.rcvWnd()).thenReturn(10L);

                                final ConnectionHandler handler = new ConnectionHandler(config, LAST_ACK, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: The only thing that can arrive in this state is an acknowledgment
                                // RFC 9293: of our FIN. If our FIN is now acknowledged,

                                // RFC 9293: delete the TCB,
                                verify(tcb).delete();

                                // RFC 9293: enter the CLOSED state,
                                assertEquals(CLOSED, handler.state);

                                verify(seg).release();
                            }
                        }

                        @Nested
                        class OnTimeWaitState {
                            @Test
                            void shouldAcknowledge() {
                                when(tcb.rcvNxt()).thenReturn(123L);
                                when(seg.seq()).thenReturn(123L);
                                when(seg.ack()).thenReturn(88L);
                                when(tcb.sndUna()).thenReturn(87L);
                                when(tcb.sndNxt()).thenReturn(88L);
                                when(tcb.rcvWnd()).thenReturn(10L);

                                final ConnectionHandler handler = new ConnectionHandler(config, TIME_WAIT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                                handler.channelRead(ctx, seg);
                                handler.channelReadComplete(ctx);

                                // RFC 9293: The only thing that can arrive in this state is a retransmission
                                // RFC 9293: of the remote FIN. Acknowledge it,
                                verify(tcb).send(eq(ctx), segmentCaptor.capture());
                                final Segment response = segmentCaptor.getValue();
                                assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                                // RFC 9293: and restart the 2 MSL timeout.
                                verify(timeWaitTimer).cancel(false);
                                assertNotNull(handler.timeWaitTimer);
                                assertNotSame(timeWaitTimer, handler.timeWaitTimer);

                                verify(seg).release();
                            }
                        }
                    }
                }

                @Nested
                class CheckData {
                    @BeforeEach
                    void setUp() {
                        when(seg.isAck()).thenReturn(true);
                    }

                    @Nested
                    class OnEstablishedAndFinWait1AndFindWait2State {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "ESTABLISHED",
                                "FIN_WAIT_1",
                                "FIN_WAIT_2"
                        })
                        void shouldAcknowledge(final State state, @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(87L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(seg.content().readableBytes()).thenReturn(100);
                            when(seg.isPsh()).thenReturn(true);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: Once in the ESTABLISHED state, it is possible to deliver segment
                            // RFC 9293: data to user RECEIVE buffers. Data from segments can be moved into
                            // RFC 9293: buffers until either the buffer is full or the segment is empty.
                            verify(tcb.receiveBuffer()).receive(eq(ctx), eq(tcb), any());

                            // RFC 9293: If the segment empties and carries a PUSH flag, then the user is
                            // RFC 9293: informed, when the buffer is returned, that a PUSH has been
                            // RFC 9293: received.
                            verify(tcb.receiveBuffer()).fireRead(ctx, tcb);

                            // RFC 9293: Send an acknowledgment of the form:
                            // RFC 9293: <SEQ=SND.NXT><ACK=RCV.NXT><CTL=ACK>
                            verify(tcb).send(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class OnCloseWaitAndClosingAndLastAckAndTimeWaitState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "CLOSE_WAIT",
                                "CLOSING",
                                // "LAST_ACK", // unreachable in our implementation
                                "TIME_WAIT"
                        })
                        void shouldDiscardSegment(final State state) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(87L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(seg.content().readableBytes()).thenReturn(100);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: This should not occur since a FIN has been received from the remote
                            // RFC 9293: side. Ignore the segment text.
                            verify(tcb.receiveBuffer(), never()).receive(any(), any(), any());

                            verify(seg).release();
                        }
                    }
                }

                @Nested
                class CheckFinBit {
                    @BeforeEach
                    void setUp() {
                        when(seg.isAck()).thenReturn(true);
                        when(seg.isFin()).thenReturn(true);
                    }

                    @Nested
                    class OnSynReceivedAndEstablishedState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "SYN_RECEIVED",
                                "ESTABLISHED"
                        })
                        void shouldChangeToCloseWait(final State state, @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(87L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(ctx.executor()).thenReturn(executor);
                            doAnswer(invocation -> {
                                invocation.getArgument(0, Runnable.class).run();
                                return null;
                            }).when(executor).execute(any());

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                            verify(ctx).fireUserEventTriggered(any(ConnectionClosing.class));

                            // RFC 9293: advance RCV.NXT over the FIN,
                            verify(tcb.receiveBuffer()).receive(any(), any(), any());

                            // RFC 9293: and send an acknowledgment for the FIN.
                            verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                            verify(tcb.receiveBuffer()).fireRead(any(), any());

                            // RFC 9293: Enter the CLOSE-WAIT state.
                            assertEquals(CLOSE_WAIT, handler.state);

                            verify(seg).release();
                        }
                    }

                    @Nested
                    class OnFinWait1State {
                        @Test
                        void shouldChangeToTimeWaitIfFinHasBeenAcked(@Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(87L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(ctx.executor()).thenReturn(executor);
                            doAnswer(invocation -> {
                                invocation.getArgument(0, Runnable.class).run();
                                return null;
                            }).when(executor).execute(any());

                            final ConnectionHandler handler = new ConnectionHandler(config, FIN_WAIT_1, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                            verify(ctx).fireUserEventTriggered(any(ConnectionClosing.class));

                            // RFC 9293: advance RCV.NXT over the FIN,
                            verify(tcb.receiveBuffer()).receive(any(), any(), any());

                            // RFC 9293: and send an acknowledgment for the FIN.
                            verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                            verify(tcb.receiveBuffer()).fireRead(any(), any());

                            // RFC 9293: If our FIN has been ACKed (perhaps in this segment),

                            // RFC 9293: then enter TIME-WAIT,
                            assertEquals(TIME_WAIT, handler.state);

                            // RFC 9293: start the time-wait timer,
                            assertNotNull(handler.timeWaitTimer);

                            // RFC 9293: turn off the other timers;
                            verify(userTimer).cancel(false);
                            verify(retransmissionTimer).cancel(false);
                        }

                        @Test
                        void shouldChangeToClosingIfFinHasNotBeenAcked(@Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(88L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(ctx.executor()).thenReturn(executor);
                            doAnswer(invocation -> {
                                invocation.getArgument(0, Runnable.class).run();
                                return null;
                            }).when(executor).execute(any());

                            final ConnectionHandler handler = new ConnectionHandler(config, FIN_WAIT_1, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                            verify(ctx).fireUserEventTriggered(any(ConnectionClosing.class));

                            // RFC 9293: advance RCV.NXT over the FIN,
                            verify(tcb.receiveBuffer()).receive(any(), any(), any());

                            // RFC 9293: and send an acknowledgment for the FIN.
                            verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                            verify(tcb.receiveBuffer()).fireRead(any(), any());

                            // RFC 9293: otherwise, enter the CLOSING state.
                            assertEquals(CLOSING, handler.state);
                        }
                    }

                    @Nested
                    class OnFinWait2State {
                        @Test
                        void shouldEnterTimeWaitState(@Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(87L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(ctx.executor()).thenReturn(executor);
                            doAnswer(invocation -> {
                                invocation.getArgument(0, Runnable.class).run();
                                return null;
                            }).when(executor).execute(any());

                            final ConnectionHandler handler = new ConnectionHandler(config, FIN_WAIT_2, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                            verify(ctx).fireUserEventTriggered(any(ConnectionClosing.class));

                            // RFC 9293: advance RCV.NXT over the FIN,
                            verify(tcb.receiveBuffer()).receive(any(), any(), any());

                            // RFC 9293: and send an acknowledgment for the FIN.
                            verify(tcb).sendAndFlush(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                            verify(tcb.receiveBuffer()).fireRead(any(), any());

                            // RFC 9293: Enter the TIME-WAIT state.
                            assertEquals(TIME_WAIT, handler.state);

                            // RFC 9293: start the time-wait timer,
                            assertNotNull(handler.timeWaitTimer);

                            // RFC 9293: turn off the other timers;
                            verify(userTimer).cancel(false);
                            verify(retransmissionTimer).cancel(false);
                        }
                    }

                    @Nested
                    @Disabled("unreachable with our implementation")
                    class OnCloseWaitAndClosingAndLastAckState {
                        @ParameterizedTest
                        @EnumSource(value = State.class, names = {
                                "CLOSE_WAIT",
                                "CLOSING",
                                "LAST_ACK"
                        })
                        void shouldRemainInState(final State state) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(88L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(tcb.maxSndWnd()).thenReturn(64000L);

                            final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                            verify(ctx).fireUserEventTriggered(any(ConnectionClosing.class));

                            // RFC 9293: advance RCV.NXT over the FIN,
                            verify(tcb.receiveBuffer()).receive(any(), any(), any());

                            // RFC 9293: and send an acknowledgment for the FIN.
                            verify(tcb).send(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                            verify(tcb.receiveBuffer()).fireRead(any(), any());

                            // RFC 9293: remain in the state.
                            assertEquals(state, handler.state);
                        }
                    }

                    @Nested
                    class OnTimeWaitState {
                        @Test
                        void shouldRemainInStateAndRestartTimeWaitTimer(@Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
                            when(tcb.rcvNxt()).thenReturn(123L);
                            when(seg.seq()).thenReturn(123L);
                            when(seg.ack()).thenReturn(88L);
                            when(tcb.sndUna()).thenReturn(87L);
                            when(tcb.sndNxt()).thenReturn(88L);
                            when(tcb.rcvWnd()).thenReturn(10L);
                            when(ctx.executor()).thenReturn(executor);
                            doAnswer(invocation -> {
                                invocation.getArgument(0, Runnable.class).run();
                                return null;
                            }).when(executor).execute(any());

                            final ConnectionHandler handler = new ConnectionHandler(config, TIME_WAIT, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                            handler.channelRead(ctx, seg);
                            handler.channelReadComplete(ctx);

                            // RFC 9293: If the FIN bit is set, signal the user "connection closing"
                            verify(ctx).fireUserEventTriggered(any(ConnectionClosing.class));

                            // RFC 9293: advance RCV.NXT over the FIN,
                            verify(tcb.receiveBuffer()).receive(any(), any(), any());

                            // RFC 9293: and send an acknowledgment for the FIN.
                            verify(tcb).send(eq(ctx), segmentCaptor.capture());
                            final Segment response = segmentCaptor.getValue();
                            assertThat(response, allOf(seq(88L), ack(123L), ctl(ACK)));

                            // RFC 9293: Note that FIN implies PUSH for any segment text not yet delivered to the user.
                            verify(tcb.receiveBuffer()).fireRead(any(), any());

                            // RFC 9293: Remain in the TIME-WAIT state.
                            assertEquals(TIME_WAIT, handler.state);

                            // RFC 9293: Restart the 2 MSL time-wait timeout.
                            verify(timeWaitTimer).cancel(false);
                            assertNotNull(handler.timeWaitTimer);
                            assertNotSame(timeWaitTimer, handler.timeWaitTimer);
                        }
                    }
                }
            }
        }

        // RFC 9293: 3.10.8.  Timeouts
        // https://www.rfc-editor.org/rfc/rfc9293.html#name-timeouts
        @Nested
        class Timeouts {
            @Nested
            class UserTimeout {
                @ParameterizedTest
                @EnumSource(value = State.class, names = {
                        "LISTEN",
                        "SYN_SENT",
                        "SYN_RECEIVED",
                        "ESTABLISHED",
                        "FIN_WAIT_1",
                        "FIN_WAIT_2",
                        "CLOSE_WAIT",
                        "CLOSING",
                        "LAST_ACK",
                        "TIME_WAIT",
                        "CLOSED"
                })
                void shouldCloseConnection(final State state) {
                    final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                    handler.userTimeout(ctx);

                    // RFC 9293: For any state if the user timeout expires,

                    // RFC 9293: flush all queues,
                    verify(tcb.sendBuffer()).fail(any(ConnectionAbortedDueToUserTimeoutException .class));
                    verify(tcb.retransmissionQueue()).release();
                    verify(tcb.receiveBuffer()).release();

                    // RFC 9293: signal the user "error: connection aborted due to user timeout" in
                    // RFC 9293: general and for any outstanding calls,
                    verify(ctx).fireExceptionCaught(any(ConnectionAbortedDueToUserTimeoutException.class));

                    // RFC 9293: delete the TCB,
                    verify(tcb).delete();

                    // RFC 9293: enter the CLOSED state,
                    assertEquals(CLOSED, handler.state);

                    assertNull(handler.userTimer);
                }
            }

            @Nested
            class RetransmissionTimeout {
                @ParameterizedTest
                @EnumSource(value = State.class, names = {
                        "LISTEN",
                        "SYN_SENT",
                        "SYN_RECEIVED",
                        "ESTABLISHED",
                        "FIN_WAIT_1",
                        "FIN_WAIT_2",
                        "CLOSE_WAIT",
                        "CLOSING",
                        "LAST_ACK",
                        "TIME_WAIT",
                        "CLOSED"
                })
                void shouldRetransmitEarliestSegment(final State state, @Mock(answer = RETURNS_DEEP_STUBS) final Segment seg) {
                    when(tcb.rto()).thenReturn(1234L);
                    when(tcb.flightSize()).thenReturn(64_000L);
                    when(tcb.smss()).thenReturn(1000L);
                    when(tcb.retransmissionQueue().nextSegment()).thenReturn(seg);
                    when(tcb.effSndMss()).thenReturn(1401L);
                    when(tcb.cwnd()).thenReturn(500L);
                    when(seg.content()).thenReturn(Unpooled.buffer());

                    final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, null);

                    final long rto = 1234L;
                    handler.retransmissionTimeout(ctx, tcb, rto);

                    // RFC 6298: (5.4) Retransmit the earliest segment that has not been acknowledged by the
                    // RFC 6298:       TCP receiver.
                    verify(ctx).writeAndFlush(any(Segment.class));

                    // RFC 6298: (5.5) The host MUST set RTO <- RTO * 2 ("back off the timer"). The maximum
                    // RFC 6298:       value discussed in (2.5) above may be used to provide an upper bound
                    // RFC 6298:       to this doubling operation.
                    verify(tcb).rto(2468L);

                    // RFC 6298: (5.6) Start the retransmission timer, such that it expires after RTO
                    // RFC 6298:       seconds (for the value of RTO after the doubling operation outlined
                    // RFC 6298:       in 5.5).
                    assertNotNull(handler.retransmissionTimer);
                    assertNotSame(retransmissionTimer, handler.retransmissionTimer);

                    // RFC 5681: When a TCP sender detects segment loss using the retransmission timer and
                    // RFC 5681: the given segment has not yet been resent by way of the retransmission
                    // RFC 5681: timer, the value of ssthresh MUST be set to no more than the value given in
                    // RFC 5681: equation (4):
                    // RFC 5681: ssthresh = max (FlightSize / 2, 2*SMSS) (4)
                    verify(tcb).ssthresh(32_000);

                    // RFC 5681: Furthermore, upon a timeout (as specified in [RFC2988]) cwnd MUST be set to
                    // RFC 5681: no more than the loss window, LW, which equals 1 full-sized segment
                    // RFC 5681: (regardless of the value of IW).  Therefore, after retransmitting the
                    // RFC 5681: dropped segment the TCP sender uses the slow start algorithm to increase
                    // RFC 5681: the window from 1 full-sized segment to the new value of ssthresh, at which
                    // RFC 5681: point congestion avoidance again takes over.
                    verify(tcb).cwnd(1401L);
                }
            }

            @Nested
            class TimeWaitTimeout {
                @ParameterizedTest
                @EnumSource(value = State.class, names = {
                        "LISTEN",
                        "SYN_SENT",
                        "SYN_RECEIVED",
                        "ESTABLISHED",
                        "FIN_WAIT_1",
                        "FIN_WAIT_2",
                        "CLOSE_WAIT",
                        "CLOSING",
                        "LAST_ACK",
                        "TIME_WAIT",
                        "CLOSED"
                })
                void shouldCloseConnection(final State state) {
                    final ConnectionHandler handler = new ConnectionHandler(config, state, tcb, userTimer, retransmissionTimer, timeWaitTimer, establishedPromise, closedPromise, ctx);

                    handler.timeWaitTimeout(ctx);

                    // RFC 9293: If the time-wait timeout expires on a connection,
                    // RFC 9293: delete the TCB,
                    verify(tcb).delete();

                    // RFC 9293: enter the CLOSED state,
                    assertEquals(CLOSED, handler.state);

                    assertNull(handler.timeWaitTimer);
                }
            }
        }
    }
}
