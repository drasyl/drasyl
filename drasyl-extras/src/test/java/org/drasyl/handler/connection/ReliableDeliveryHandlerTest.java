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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.connection.RetransmissionQueue.Clock;
import org.drasyl.handler.connection.SegmentOption.TimestampsOption;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMillis;
import static org.awaitility.Awaitility.await;
import static org.drasyl.handler.connection.Segment.SEG_HDR_SIZE;
import static org.drasyl.handler.connection.SegmentOption.TIMESTAMPS;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.handler.connection.State.CLOSING;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.handler.connection.State.FIN_WAIT_1;
import static org.drasyl.handler.connection.State.FIN_WAIT_2;
import static org.drasyl.handler.connection.State.LAST_ACK;
import static org.drasyl.handler.connection.State.LISTEN;
import static org.drasyl.handler.connection.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.State.SYN_SENT;
import static org.drasyl.handler.connection.TransmissionControlBlock.OVERRIDE_TIMEOUT;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReliableDeliveryHandlerTest {
    @Test
    void exceptionsShouldCloseTheConnection(@Mock final Throwable cause) {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
        channel.pipeline().addLast(handler);

        channel.pipeline().fireExceptionCaught(cause);

        assertEquals(CLOSED, handler.state);
    }

    // "Server" is in CLOSED state
    // "Client" initiate handshake
    @Nested
    class ConnectionEstablishment {
        @Nested
        class SuccessfulSynchronization {
            @Nested
            class ClientSide {
                // RFC 9293, Figure 6, TCP Peer A
                // active client, passive server
                @Test
                void clientShouldSynchronizeWhenServerBehavesExpectedly() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                        final long iss = 100;
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64 * 1220, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 1220 + SEG_HDR_SIZE);
                    }, true, null, CLOSED, null, null, false, false);
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                    Segment actual = channel.readOutbound();
                    assertEquals(Segment.syn(100, 64_000), actual);
                    assertEquals(SYN_SENT, handler.state);

                    assertEquals(100, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(0, handler.tcb.rcvNxt());

                    // peer SYNchronizes his SEG with us and ACKed our segment, we reply with ACK for his SYN
                    channel.writeInbound(Segment.synAck(300, 101, 64_000));
                    assertEquals(Segment.ack(101, 301), channel.readOutbound());
                    assertEquals(ESTABLISHED, handler.state);

                    assertEquals(101, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(301, handler.tcb.rcvNxt());

                    assertTrue(channel.isOpen());
                    channel.close();
                }

                // RFC 9293, Figure 7, TCP Peer A (and also TCP Peer B)
                // active client, active server
                // Both peers are in CLOSED state
                // Both peers initiate handshake simultaneous
                @Test
                void clientShouldSynchronizeIfServerPerformsSimultaneousHandshake() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                        final long iss = 100;
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 1200 + SEG_HDR_SIZE);
                    }, true, null, CLOSED, null, null, false, false);
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                    assertEquals(Segment.syn(100, 64_000), channel.readOutbound());
                    assertEquals(SYN_SENT, handler.state);

                    assertEquals(100, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(0, handler.tcb.rcvNxt());

                    // peer SYNchronizes his SEG before our SYN has been received
                    channel.writeInbound(Segment.syn(300, 64_000));
                    assertEquals(SYN_RECEIVED, handler.state);
                    assertEquals(Segment.synAck(100, 301, 64_000), channel.readOutbound());

                    assertEquals(100, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(301, handler.tcb.rcvNxt());

                    // peer respond to our SYN with ACK (and another SYN)
                    channel.writeInbound(Segment.synAck(301, 101, 64_000));
                    assertEquals(ESTABLISHED, handler.state);

                    assertEquals(101, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(301, handler.tcb.rcvNxt());

                    assertTrue(channel.isOpen());
                    channel.close();
                }
            }

            @Nested
            class ServerSide {
                // RFC 9293, Figure 6, TCP Peer B
                @Test
                void passiveServerShouldSynchronizeWhenClientBehavesExpectedly() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                        final long iss = 300;
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 1200 + SEG_HDR_SIZE);
                    }, false, null, CLOSED, null, null, false, false);
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should change state to LISTEN
                    assertEquals(LISTEN, handler.state);

                    // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
                    channel.writeInbound(Segment.syn(100, 64_000));
                    assertEquals(Segment.synAck(300, 101, 64_000), channel.readOutbound());
                    assertEquals(SYN_RECEIVED, handler.state);

                    assertEquals(300, handler.tcb.sndUna());
                    assertEquals(301, handler.tcb.sndNxt());
                    assertEquals(101, handler.tcb.rcvNxt());

                    // peer ACKed our SYN
                    // we piggyback some data, that should also be processed by the server
                    final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                    channel.writeInbound(Segment.pshAck(101, 301, data));
                    assertEquals(ESTABLISHED, handler.state);

                    assertEquals(301, handler.tcb.sndUna());
                    assertEquals(301, handler.tcb.sndNxt());
                    assertEquals(111, handler.tcb.rcvNxt());
                    assertEquals(data, channel.readInbound());

                    assertTrue(channel.isOpen());
                    channel.close();
                    data.release();
                }

                // passive server, passive client
                // server write should trigger handshake
                @Test
                void passiveServerShouldSwitchToActiveOpenOnWrite() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                        final long iss = 100;
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64 * 1220, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 1220 + SEG_HDR_SIZE);
                    }, false, null, CLOSED, null, null, false, false);
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should change state to LISTEN
                    assertEquals(LISTEN, handler.state);

                    // write should perform an active OPEN handshake
                    final ByteBuf data = Unpooled.buffer(3).writeBytes(randomBytes(3));
                    channel.writeOutbound(data);
                    final Segment actual = channel.readOutbound();
                    assertEquals(Segment.syn(100, 64_000), actual);
                    assertEquals(SYN_SENT, handler.state);

                    // after handshake, the write should be formed
                    channel.writeInbound(Segment.synAck(300, 101, 64_000));
                    assertEquals(ESTABLISHED, handler.state);
                    assertEquals(Segment.pshAck(101, 301, data), channel.readOutbound());

                    channel.close();
                    data.release();
                }
            }
        }

        @Nested
        class DeadPeer {
            @Nested
            class ClientSide {
                // server is not responding to the SYN
                @Test
                void clientShouldCloseChannelIfServerIsNotRespondingToSyn() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), true, CLOSED, 1200 + SEG_HDR_SIZE, 64_000);
                    channel.pipeline().addLast(handler);

                    // peer is dead and therefore no SYN/ACK is received
                    // wait for user timeout
                    await().atMost(ofHours(1)).untilAsserted(() -> {
                        channel.runScheduledPendingTasks();
                        assertEquals(CLOSED, handler.state);
                    });

                    assertThrows(ConnectionHandshakeException.class, channel::checkException);
                    assertFalse(channel.isOpen());
                    assertNull(handler.tcb);
                }
            }

            @Nested
            class ServerSide {
                // client is not responding to the SYN/ACK
                @Test
                void serverShouldCloseChannelIfClientIsNotRespondingToSynAck() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), false, CLOSED, 1200 + SEG_HDR_SIZE, 64_000);
                    channel.pipeline().addLast(handler);

                    // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
                    channel.writeInbound(Segment.syn(100, 64_000));

                    // peer is dead and therefore no ACK is received
                    // wait for user timeout
                    await().atMost(ofHours(1)).untilAsserted(() -> {
                        channel.runScheduledPendingTasks();
                        assertEquals(CLOSED, handler.state);
                    });

                    assertThrows(ConnectionHandshakeException.class, channel::checkException);
                    assertFalse(channel.isOpen());
                    assertNull(handler.tcb);
                }
            }
        }

        @Nested
        class HalfOpenDiscovery {
            // RFC 9293, Figure 9, TCP Peer A
            // we've crashed
            // peer is in ESTABLISHED state
            @Test
            void weShouldResetPeerIfWeHaveDiscoveredThatWeHaveCrashed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                    final long iss = 400;
                    return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 1200 + SEG_HDR_SIZE);
                }, true, null, CLOSED, null, null, false, false);
                channel.pipeline().addLast(handler);

                // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                assertEquals(Segment.syn(400, 64_000), channel.readOutbound());
                assertEquals(SYN_SENT, handler.state);

                assertEquals(400, handler.tcb.sndUna());
                assertEquals(401, handler.tcb.sndNxt());
                assertEquals(0, handler.tcb.rcvNxt());

                // as we got an ACK for an unexpected seq, reset the peer
                channel.writeInbound(Segment.ack(300, 100));
                assertEquals(Segment.rst(100), channel.readOutbound());
                assertEquals(SYN_SENT, handler.state);

                assertEquals(400, handler.tcb.sndUna());
                assertEquals(401, handler.tcb.sndNxt());
                assertEquals(0, handler.tcb.rcvNxt());

                assertTrue(channel.isOpen());
                channel.close();
            }

            // RFC 9293, Figure 9, TCP Peer B
            // we're in ESTABLISHED state
            // peer has crashed
            @Test
            void weShouldCloseOurConnectionIfPeerHasDiscoveredThatPeerHasCrashed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, true, null, ESTABLISHED, new TransmissionControlBlock(300L, 300L, 1220 * 64, 300L, 100L, 1220 * 64, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                // other wants to SYNchronize with us, ACK with our expected seq
                channel.writeInbound(Segment.syn(400, 64_000));
                assertEquals(ESTABLISHED, handler.state);
                assertEquals(Segment.ack(300, 100), channel.readOutbound());

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(300, handler.tcb.sndNxt());
                assertEquals(100, handler.tcb.rcvNxt());

                // as we sent an ACK for an unexpected seq, peer will reset us
                final Segment msg = Segment.rst(100);
                assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(msg));
                assertEquals(CLOSED, handler.state);
                assertNull(handler.tcb);
            }

            // RFC 9293, Figure 10, TC Peer A
            @Test
            void shouldResetRemotePeerIfWeReceiveDataInAnUnsynchronizedState() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> {
                            final long iss = Segment.randomSeq();
                            return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 100 + SEG_HDR_SIZE);
                        }, false, null, LISTEN, null, null, true, false);
                channel.pipeline().addLast(handler);

                final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                final Segment seg = Segment.ack(300, 100, data);
                channel.writeInbound(seg);
                assertEquals(Segment.rst(100), channel.readOutbound());

                channel.close();
            }

            // RFC 9293, Figure 11, TC Peer B
            @Test
            void duplicateSynShouldCauseUsToReturnToListenState() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> {
                    final long iss = 200;
                    return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch), new ReceiveBuffer(ch), 100 + SEG_HDR_SIZE);
                }, false, null, LISTEN, null, null, true, false);
                channel.pipeline().addLast(handler);

                // old duplicate ACK arrives at us
                long x = 200;
                long z = 100;
                channel.writeInbound(Segment.syn(z));
                assertEquals(Segment.synAck(x, z + 1), channel.readOutbound());

                // returned SYN/ACK causes a RST, we should return to LISTEN
                channel.writeInbound(Segment.rst(z + 1));
                assertEquals(LISTEN, handler.state);

                channel.close();
            }
        }

        @Nested
        class AbortedSynchronization {
            @Test
            void shouldCancelOpenCallAndCloseChannel() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(1_000), ch -> null, true, null, CLOSED, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, false, false);
                channel.pipeline().addLast(handler);

                channel.pipeline().close();
            }
        }
    }

    @Nested
    class ConnectionClearing {
        @Nested
        class SuccessfulClearing {
            // RFC 9293, Figure 12, TCP Peer A
            // Both peers are in ESTABLISHED state
            // we close
            @Test
            void weShouldPerformNormalCloseSequenceOnChannelClose() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(100L, 100L, 1220 * 64, 100L, 300L, 1220 * 64, 300L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.pipeline().close();
                assertEquals(FIN_WAIT_1, handler.state);
                assertEquals(Segment.finAck(100, 300), channel.readOutbound());
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // my close got ACKed
                channel.writeInbound(Segment.ack(300, 101));
                assertEquals(FIN_WAIT_2, handler.state);
                assertFalse(future.isDone());

                assertEquals(101, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // peer now triggers close as well
                channel.writeInbound(Segment.finAck(300, 101));
                assertEquals(Segment.ack(101, 301), channel.readOutbound());
                assertEquals(CLOSED, handler.state);
                assertTrue(future.isDone());

                // FIXME: wieder einbauen?
                //assertNull(handler.tcb);
            }

            // RFC 9293, Figure 12, TCP Peer B
            // Both peers are in ESTABLISHED state
            // other peer close
            @Test
            void weShouldPerformNormalCloseSequenceWhenPeerInitiateClose() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(299L, 300L, 1220 * 64, 300L, 100L, 1220 * 64, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                // peer triggers close
                channel.writeInbound(Segment.finAck(100, 300));

                // we should trigger a close as well
                assertEquals(LAST_ACK, handler.state);
                assertEquals(Segment.finAck(300, 101), channel.readOutbound());

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(301, handler.tcb.sndNxt());
                assertEquals(101, handler.tcb.rcvNxt());

                // peer ACKed our close
                channel.writeInbound(Segment.ack(101, 301));

                assertEquals(CLOSED, handler.state);
                assertNull(handler.tcb);
            }

            // RFC 9293, Figure 13, TCP Peer A (and also TCP Peer B)
            // Both peers are in ESTABLISHED state
            // Both peers initiate close simultaneous
            @Test
            void weShouldPerformSimultaneousCloseIfBothPeersInitiateACloseAtTheSameTime() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(100L, 100L, 1220 * 64, 100L, 300L, 1220 * 64, 300L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.close();
                assertEquals(FIN_WAIT_1, handler.state);
                assertEquals(Segment.finAck(100, 300), channel.readOutbound());
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // got parallel close
                channel.writeInbound(Segment.finAck(300, 100));
                assertEquals(CLOSING, handler.state);
                assertEquals(Segment.ack(101, 301), channel.readOutbound());
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(301, handler.tcb.rcvNxt());

                channel.writeInbound(Segment.ack(301, 101));

                await().untilAsserted(() -> {
                    channel.runScheduledPendingTasks();
                    assertEquals(CLOSED, handler.state);
                });

                assertTrue(future.isDone());
                assertNull(handler.tcb);
            }

            @Test
            void shouldSentRemainingDataBeforeClose() {
                // FIXME
            }
        }

        @Nested
        class DeadPeer {
            // We're in ESTABLISHED state
            // Peer is dead
            @Test
            void weShouldSucceedCloseSequenceIfPeerIsDead() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.pipeline().close();
                assertFalse(future.isDone());

                // wait for user timeout
                await().untilAsserted(() -> {
                    channel.runScheduledPendingTasks();
                    assertEquals(CLOSED, handler.state);
                });

                assertFalse(channel.isOpen());
                assertNull(handler.tcb);
                assertTrue(future.isDone());
                assertFalse(future.isSuccess());
            }
        }
    }

    @Nested
    class Transmission {
        // FIXME: send buffer should keep track what bytes have been enqueued before flush was triggered
        @Test
        void shouldOnlySendDataEnqueuedBeforeFlush() {
            // teil des handlers oder TCB?
        }

        @Test
        void shouldPassReceivedDataCorrectlyToApplication() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 301L, 1000, 100L, 100, 100L, 1000 + SEG_HDR_SIZE);
            final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
            channel.pipeline().addLast(handler);

            final ByteBuf receivedData = Unpooled.buffer(100).writeBytes(randomBytes(100));
            channel.writeInbound(Segment.ack(100, 301L, 1000, receivedData));

            final ByteBuf dataPassedToApplication = channel.readInbound();
            assertEquals(receivedData, dataPassedToApplication);

            channel.close();
        }

        @Nested
        class Mss {
            @Test
            void shouldSegmentizeDataIntoSegmentsNoLargerThanMss() {
                final int bytes = 300;
                final int effSndMss = 100;

                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(100L, 100L, 1000, 100L, 300L, 1000, 300L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), effSndMss + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                // as effSndMss is set to 100, the buf will be segmetized into 100 byte long segments. The last has the PSH flag set.
                final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeOutbound(data);

                for (int i = 0; i < bytes - effSndMss; i += effSndMss) {
                    assertEquals(Segment.ack(100 + i, 300, data.slice(i, effSndMss)), channel.<Segment>readOutbound());
                }

                assertEquals(Segment.pshAck(300, 300, data.slice(200, effSndMss)), channel.<Segment>readOutbound());

                channel.close();
            }
        }

        @Nested
        class Window {
            @Nested
            class SendWindow {
                @Test
                @Disabled("Nagle Algorithm macht diesen Test überflüssig?")
                void senderShouldRespectSndWndWhenWritingToNetwork() {
                    final int bytes = 600;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 100L, 300L, 1000, 600 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, false, null, ESTABLISHED, tcb, null, false, false);
                    channel.pipeline().addLast(handler);

                    final ByteBuf data = Unpooled.buffer(3 * bytes).writeBytes(randomBytes(3 * bytes));

                    // SND.WND = 1000, everything should be written to network
                    channel.writeOutbound(data.slice(0, bytes));
                    assertEquals(Segment.pshAck(100, 300, data.slice(0, bytes)), channel.readOutbound());

                    // SND.WND = 400, just 400 bytes allowed
                    channel.writeInbound(Segment.ack(300, 700, 400));
                    channel.writeOutbound(data.slice(600, bytes));
                    assertEquals(Segment.pshAck(700, 300, data.slice(600, 400)), channel.readOutbound());

                    // send ack for the first segment. The remaining 200 bytes should then be sent
                    channel.writeInbound(Segment.ack(300, 700, 600));
                    assertEquals(Segment.pshAck(1100, 300, data.slice(1000, 200)), channel.readOutbound());

                    channel.close();
                }

                @Test
                void zeroWindowProbing() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 300L, 0, 100L, 1000, 100L, 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeOutbound(data);

                    // SND.WND is 0, we have to perform Zero-Window Probing
                    assertEquals(Segment.pshAck(300, 100, data.slice(0, 1)), channel.readOutbound());

                    channel.close();
                }

                // FIXME: Die ganzen Connection Failures MUSTS aus Appendix B aus rfc9293 fehlen noch
                // https://datatracker.ietf.org/doc/html/rfc9293#name-tcp-requirement-summary

                @Test
                void senderShouldHandleSentSegmentsToBeAcknowledgedJustPartially() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 600L, 0, 100L, 100L, 1000, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    // 300 bytes in flight, only first 100 are ACKed
                    channel.writeInbound(Segment.ack(100, 400));

                    assertEquals(400, tcb.sndUna());
                    assertEquals(600, tcb.sndNxt());

                    channel.close();
                }
            }

            @Nested
            class ReceiveWindow {
                @Test
                void receiverShouldUpdateRcvWnd() {
                    final int bytes = 600;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 1000, 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    // initial value
                    assertEquals(1000, tcb.rcvWnd());

                    // 600 bytes added to RCV.BUF
                    final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                    channel.writeInbound(Segment.ack(100, 600, data));

                    assertEquals(400, tcb.rcvWnd());

                    // RCV.BUF flushed
                    channel.read();
                    assertEquals(1000, tcb.rcvWnd());

                    channel.close();
                }

                @Test
                void shouldOnlyAcceptAsManyBytesAsSpaceAvailableInReceiveBuffer() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 301L, 1000, 100L, 60, 100L, 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    // we got more than we are willing to accept
                    final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeInbound(Segment.ack(100, 301L, 1000, data));

                    // we ACK just the part we have accepted
                    assertEquals(channel.readOutbound(), Segment.ack(301, 160));

                    channel.close();
                }

                // FIXME: Wir erhalten ACK welcher nur ein Teil eines SEG in unserer RetransmissionQueue bestätigt. RetransmissionQueue muss bestätigten Teil entfernen und rest neu packen
                @Disabled
                @Test
                void receiverShouldAbleToAckSegmentWhichContainsOnlyPartiallyNewSegments() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 301L, 1000, 100L, 60, 100L, 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, false, false);
                    channel.pipeline().addLast(handler);

                    // we got more than we are willing to accept
                    final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeInbound(Segment.ack(100, 301L, 1000, data));

                    // we ACK just the part we have accepted
                    assertEquals(channel.readOutbound(), Segment.ack(301, 160));

                    // SEG not fully ACKed, we send again
                    final ByteBuf data2 = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeInbound(Segment.ack(100, 301L, 1000, data2));

                    channel.close();
                }
            }

            @Nested
            class SillyWindowSyndrome {
                // RFC 9293, Section 3.8.6.2.1 Sender's Algorithm -- When to Send Data
                // https://www.rfc-editor.org/rfc/rfc9293.html#SWSsender
                // RFC 9293, Section 3.7.4 Nagle algorithm
                @Test
                void senderShouldAvoidTheSillyWindowSyndrome() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    TransmissionControlBlock tcb = new TransmissionControlBlock(600L, 600L, 1000, 100L, 100L, 1000, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 100 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    final ByteBuf buf = Unpooled.buffer(1_000).writeBytes(randomBytes(1_000));

                    // as there is no unacknowledged data (i.e., SND.NXT > SND.UNA), data should be send immediately
                    channel.writeOutbound(buf.slice(0, 50));
                    assertEquals(Segment.pshAck(600, 100, 1_000, buf.slice(0, 50)), channel.readOutbound());

                    // unacknowledged data, data should be delayed
                    channel.writeOutbound(buf.slice(50, 50));
                    assertNull(channel.readOutbound());

                    // unacknowledged data, but we can send a maximum-sized segment
                    channel.writeOutbound(buf.slice(100, 60));
                    assertEquals(Segment.pshAck(650, 100, 1_000, buf.slice(50, 100)), channel.readOutbound());
                    // but do not send the remainder in a second small segment...
                    assertNull(channel.readOutbound());
                    // ...until override timeout occurs
                    await().atMost(ofMillis(OVERRIDE_TIMEOUT * 2)).untilAsserted(() -> {
                        channel.runScheduledPendingTasks();
                        assertEquals(Segment.pshAck(750, 100, 1_000, buf.slice(150, 10)), channel.readOutbound());
                    });

                    channel.close();
                }

                // RFC 9293, Section 3.8.6.2.2 Receiver's Algorithm -- When to Send a Window Update
                // https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.2
                @Test
                void receiverShouldAvoidTheSillyWindowSyndrome() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(600L, 600L, 1600, 100L, 100L, 1600, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 100 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    final ByteBuf buf = Unpooled.buffer(1_000).writeBytes(randomBytes(1_000));

                    // we received a maximum-sized segment, therefore RCV.USER contains 20 bytes
                    channel.writeInbound(Segment.ack(100, 600, buf.slice(0, 20)));
                    assertEquals(1600, tcb.rcvBuff());
                    assertEquals(20, tcb.rcvUser());
                    assertEquals(1580, tcb.rcvWnd());

                    ChannelHandlerContext ctx;

                    // we now read RCV.USER
                    ctx = channel.pipeline().context(handler);
                    tcb.receiveBuffer().fireRead(ctx, tcb);
                    assertEquals(1600, tcb.rcvBuff());
                    assertEquals(0, tcb.rcvUser());
                    assertEquals(1580, tcb.rcvWnd());

                    // we received a maximum-sized segment, therefore RCV.USER contains 80 bytes
                    channel.writeInbound(Segment.ack(120, 600, buf.slice(20, 80)));
                    assertEquals(1600, tcb.rcvBuff());
                    assertEquals(80, tcb.rcvUser());
                    assertEquals(1500, tcb.rcvWnd());

                    // we now read RCV.USER
                    ctx = channel.pipeline().context(handler);
                    tcb.receiveBuffer().fireRead(ctx, tcb);
                    assertEquals(1600, tcb.rcvBuff());
                    assertEquals(0, tcb.rcvUser());
                    assertEquals(1600, tcb.rcvWnd());

                    channel.close();
                }
            }
        }

        @Nested
        class CongestionControl {
            // FIXME: lost seg (single/multiple?)
            // can be caused by a lost SEG
            @Test
            void receiverShouldRespondWithExpectedSegToUnexpectedSeg() {
                final int bytes = 600;

                final EmbeddedChannel channel = new EmbeddedChannel();
                TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 1000, 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                // SEG 100 is expected, but we send next SEG 400
                final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(400, 600, data));

                // we get ACK with expected SEG 100 number
                assertEquals(Segment.ack(600, 100), channel.readOutbound());

                channel.close();
            }

            @Test
            void receiverShouldBufferReceivedOutOfOrderSegments() {
                final int bytes = 300;

                final EmbeddedChannel channel = new EmbeddedChannel();
                TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 2000, 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                // SEG 100 is expected, but we send next SEG 700
                final ByteBuf data1 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(700, 600, data1));

                assertEquals(100, tcb.rcvNxt());
                assertEquals(1700, tcb.rcvWnd());
                // ignore ACK, not checked in this test
                assertNotNull(channel.readOutbound());

                // SEG 100 is expected, but we send next SEG 400
                final ByteBuf data2 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(400, 600, data2));

                assertEquals(100, tcb.rcvNxt());
                assertEquals(1400, tcb.rcvWnd());
                // ignore ACK, not checked in this test
                assertNotNull(channel.readOutbound());

                // SEG 100 is expected, but we send next SEG 1300
                final ByteBuf data3 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(1300, 600, data3));

                assertEquals(100, tcb.rcvNxt());
                assertEquals(1100, tcb.rcvWnd());
                // ignore ACK, not checked in this test
                assertNotNull(channel.readOutbound());

                // SEG 100 is expected, but we send next SEG 1000
                final ByteBuf data4 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(1000, 600, data4));

                assertEquals(100, tcb.rcvNxt());
                assertEquals(800, tcb.rcvWnd());
                // ignore ACK, not checked in this test
                assertNotNull(channel.readOutbound());

                // now send expected SEG 100 (should close the gap)
                final ByteBuf data5 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(100, 600, data5));

                assertEquals(1600, tcb.rcvNxt());
                // we should get ACK for everything
                assertEquals(Segment.ack(600, 1600), channel.readOutbound());
                channel.read();

                ByteBuf passedToApplication = channel.readInbound();
                assertEquals(2000, tcb.rcvWnd());
                assertEquals(1500, passedToApplication.readableBytes());

                channel.close();
            }

            @Test
            void receiverShouldNotBufferReceivedDuplicateSegments() {
                final int bytes = 300;

                final EmbeddedChannel channel = new EmbeddedChannel();
                TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 2000, 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                // SEG 100 is expected, but we send next SEG 700
                final ByteBuf data1 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(700, 600, data1));

                assertEquals(100, tcb.rcvNxt());
                assertEquals(1700, tcb.rcvWnd());
                // ignore ACK, not checked in this test
                assertNotNull(channel.readOutbound());

                // SEG 100 is expected, but we send next SEG 700 again!
                final ByteBuf data2 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeInbound(Segment.ack(700, 600, data2));

                assertEquals(100, tcb.rcvNxt());
                assertEquals(1700, tcb.rcvWnd());
                // ignore ACK, not checked in this test
                assertNotNull(channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class Retransmission {
            // RFC 5681, Section 3.1
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            @Test
            void slowStartAndCongestionAvoidance() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 6001L, 100L, 200L, 4 * 1000, 1000);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                // initial cwnd is 3 segments
                assertEquals(3 * 1000, tcb.cwnd());
                // The initial value of ssthresh SHOULD be set arbitrarily high (e.g.,
                //   to the size of the largest possible advertised window)
                assertEquals(4 * 1000, tcb.ssthresh());

                //
                // do slow start
                //
                assertTrue(tcb.doSlowStart());

                // During slow start, a TCP increments cwnd by at most SMSS bytes for
                //   each ACK received that cumulatively acknowledges new data.

                // old data
                channel.writeInbound(Segment.ack(200, 300));
                assertEquals(3000, tcb.cwnd());

                // new data
                channel.writeInbound(Segment.ack(200, 310));
                assertEquals(3010, tcb.cwnd());

                // limit to SMSS
                channel.writeInbound(Segment.ack(200, 2000));
                assertEquals(4010, tcb.cwnd());

                //
                // do congestion avoidance
                //
                assertFalse(tcb.doSlowStart());

                channel.writeInbound(Segment.ack(200, 3000));
                assertEquals(4010 + 250, tcb.cwnd());

                channel.writeInbound(Segment.ack(200, 4000));
                assertEquals(4260 + 235, tcb.cwnd());

                channel.writeInbound(Segment.ack(200, 4100));
                assertEquals(4495 + 223, tcb.cwnd());

                channel.writeInbound(Segment.ack(200, 4105));
                assertEquals(4718 + 212, tcb.cwnd());
            }

            // RFC 5681, Section 3.1, Rule 5.1
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            @Test
            void timerShouldBeStartedWhenSegmentWithDataIsSent() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final RetransmissionQueue queue = new RetransmissionQueue(channel);
                TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 300L, 2000, 100L, 100L, 2000, 100L, new SendBuffer(channel), queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                channel.writeOutbound(Unpooled.buffer(10).writeBytes(randomBytes(10)));

                assertNotNull(queue.retransmissionTimer);
            }

            // RFC 5681, Section 3.1, Rule 5.2
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            @Test
            void timerShouldBeCancelledWhenAllSegmentsHaveBeenAcked(@Mock final SendBuffer buffer,
                                                                    @Mock final ScheduledFuture timer) {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final RetransmissionQueue queue = new RetransmissionQueue(channel);
                TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 600L, 2000, 100L, 100L, 2000, 100L, buffer, queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);
                queue.retransmissionTimer = timer;

                channel.writeInbound(Segment.ack(200, 301));

                verify(timer).cancel(false);
                assertNull(queue.retransmissionTimer);
            }

            // RFC 5681, Section 3.1, Rule 5.3
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            @Test
            void timerShouldBeRestartedWhenNewSegmentsHaveBeenAcked(@Mock final SendBuffer buffer,
                                                                    @Mock final ScheduledFuture timer) {
                when(buffer.acknowledgeableBytes()).thenReturn(100L);

                final EmbeddedChannel channel = new EmbeddedChannel();
                final RetransmissionQueue queue = new RetransmissionQueue(channel);
                TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 600L, 2000, 100L, 100L, 2000, 100L, buffer, queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);
                queue.retransmissionTimer = timer;

                channel.writeInbound(Segment.ack(200, 301));

                verify(timer).cancel(false);
                assertNotNull(queue.retransmissionTimer);
            }

            // RFC 5681, Section 3.1
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            @Test
            void shouldCreateRetransmissionTimerIfAcknowledgeableSegmentIsSent() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(50), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                channel.writeOutbound(data);

                final Segment seg = Segment.pshAck(100, 300, data);
                assertEquals(seg, channel.readOutbound());

                // retransmission timer should send segment again
                await().untilAsserted(() -> {
                    channel.runScheduledPendingTasks();
                    assertEquals(seg, channel.readOutbound());
                });

                channel.close();
            }

            // RFC 5681, Section 5, Rule 5.4 - 5.6
            // https://www.rfc-editor.org/rfc/rfc6298#section-5
            @Test
            void onTimeout() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final RetransmissionQueue queue = new RetransmissionQueue(channel);
                TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 300L, 2000, 100L, 100L, 2000, 100L, new SendBuffer(channel), queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                channel.writeOutbound(Unpooled.buffer(10).writeBytes(randomBytes(10)));
                final Segment seg = channel.readOutbound();
                final ScheduledFuture<?> timer = queue.retransmissionTimer;

                // wait for timeout
                await().untilAsserted(() -> {
                    channel.runScheduledPendingTasks();

                    // retransmit
                    assertEquals(seg, channel.readOutbound());

                    // back off timer
                    assertEquals(2_000, queue.rto());

                    // start timer
                    assertNotSame(timer, queue.retransmissionTimer);
                });
            }

            // RFC 5681, Section 3.1, Rule 5.1
            // https://www.rfc-editor.org/rfc/rfc5681#section-3.1
            // algorithm known as "Reno"

            // RFC 6582
            // https://www.rfc-editor.org/rfc/rfc6582
            // algorithm "NewReno"
            @Test
            void fastRetransmit() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 6001L, 100L, 200L, 4 * 1000, 1000 + SEG_HDR_SIZE);
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                channel.pipeline().addLast(handler);

                // we need outstanding data first
                final ByteBuf outstandingData = Unpooled.buffer(100).writeBytes(randomBytes(100));
                tcb.sendBuffer().enqueue(outstandingData);
                tcb.sendBuffer().read(100);
                assertTrue(tcb.sendBuffer().hasOutstandingData());

                // three duplicate ACKs in a row
                channel.writeInbound(Segment.ack(205, 300));
                assertEquals(3000, tcb.cwnd());
                assertEquals(4000, tcb.ssthresh());
                channel.writeInbound(Segment.ack(205, 300));
                assertEquals(3000, tcb.cwnd());
                assertEquals(4000, tcb.ssthresh());
                channel.writeInbound(Segment.ack(205, 300));

                // dup ACKs should trigger immediate retransmission
                assertEquals(5943, tcb.cwnd());
                assertEquals(2850, tcb.ssthresh());
                assertEquals(Segment.ack(300, 200, outstandingData), channel.readOutbound());

                // fourth duplicate ACK
                channel.writeInbound(Segment.ack(205, 300));
                assertEquals(6943, tcb.cwnd());
                assertEquals(2850, tcb.ssthresh());
                assertNull(channel.readOutbound());

                // cumulative ACK
                channel.writeInbound(Segment.ack(205, 400));
                assertEquals(6844, tcb.cwnd());
                assertEquals(2850, tcb.ssthresh());

                // with Reno, no message is sent. But NewReno sends new data
                assertEquals(Segment.ack(400, 200, 4000), channel.readOutbound());
            }

            // FIXME: haben wir das so umgesetzt?
            //    Note that after retransmitting, once a new RTT measurement is
            //   obtained (which can only happen when new data has been sent and
            //   acknowledged), the computations outlined in Section 2 are performed,
            //   including the computation of RTO, which may result in "collapsing"
            //   RTO back down after it has been subject to exponential back off (rule
            //   5.5).

            @Nested
            class TimestampsOptionTest {
                // RFC 6298, Section 2, Rule 2.2
                // https://www.rfc-editor.org/rfc/rfc6298#section-2
                @Test
                void shouldCalculateRtoProperlyForFirstRttMeasurement(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);
                    when(clock.g()).thenReturn(0.001);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final RetransmissionQueue queue = new RetransmissionQueue(channel, 401, 0, true, clock);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 600L, 2000, 100L, 100L, 2000, 100L, new SendBuffer(channel), queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    final ByteBuf data = Unpooled.buffer(1).writeBytes(randomBytes(1));
                    final Segment seg = Segment.ack(0, 301, data);
                    seg.options().put(TIMESTAMPS, new TimestampsOption(401, 808));
                    channel.writeInbound(seg);

                    // (2.2) When the first RTT measurement R is made, the host MUST set
                    // R <- 2816 - 808 = 2008
                    // SRTT <- R
                    assertEquals(2008, queue.sRtt);
                    // RTTVAR <- R/2
                    assertEquals(1004, queue.rttVar);
                    // RTO <- SRTT + max (G, K*RTTVAR)
                    // where K = 4
                    assertEquals(6024, queue.rto());
                }

                // RFC 6298, Section 2, Rule 2.3
                // https://www.rfc-editor.org/rfc/rfc6298#section-2
                @Test
                void shouldCalculateRtoProperlyForSubsequentRttMeasurements(@Mock final Clock clock, @Mock final SendBuffer sendBuffer) {
                    when(clock.time()).thenReturn(2846L);
                    when(clock.g()).thenReturn(0.001);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final RetransmissionQueue queue = new RetransmissionQueue(channel, 401, 0, true, 1004, 2008, 6024, clock);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 8300L, 2000, 100L, 100L, 2000, 100L, sendBuffer, queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    final ByteBuf data = Unpooled.buffer(1).writeBytes(randomBytes(1));
                    final Segment seg = Segment.ack(0, 301, data);
                    seg.options().put(TIMESTAMPS, new TimestampsOption(401, 808));
                    channel.writeInbound(seg);

                    // (2.3) When a subsequent RTT measurement R' is made, a host MUST set
                    // R' <- 2846 - 808 = 2038
                    // RTTVAR <- (1 - beta) * RTTVAR + beta * |SRTT - R'|
                    assertEquals(943.125, queue.rttVar);
                    // SRTT <- (1 - alpha) * SRTT + alpha * R'
                    assertEquals(2008.9375, queue.sRtt);
                    // RTO <- SRTT + max (G, K*RTTVAR)
                    assertEquals(5781, queue.rto());
                }

                // RFC 7323, Section 4.3, Situation A: Delayed ACKs
                // https://www.rfc-editor.org/rfc/rfc7323#section-4.3
                @Test
                void timestampFromOldestUnacknowledgedSegmentIsEchoed(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(1L);
                    when(clock.g()).thenReturn(0.001);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final RetransmissionQueue queue = new RetransmissionQueue(channel, 0, 201, true, clock);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 6001L, 4 * 1000, 100L, 201L, 4 * 1000, 200L, new SendBuffer(channel), queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    Segment seg;
                    final ByteBuf data = Unpooled.buffer(1).writeBytes(randomBytes(1));

                    // <A, TSval=1> ------------------->
                    seg = Segment.ack(201, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(1, 0));
                    channel.pipeline().fireChannelRead(seg);
                    assertEquals(1, queue.tsRecent);

                    // <A, TSval=2> ------------------->
                    seg = Segment.ack(202, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(2, 0));
                    channel.pipeline().fireChannelRead(seg);
                    assertEquals(1, queue.tsRecent);

                    // <A, TSval=3> ------------------->
                    seg = Segment.ack(203, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(3, 0));
                    channel.pipeline().fireChannelRead(seg);
                    assertEquals(1, queue.tsRecent);

                    channel.pipeline().fireChannelReadComplete();

                    // <---- <ACK(C), TSecr=1>
                    final Segment response = channel.readOutbound();
                    final TimestampsOption tsOpt = (TimestampsOption) response.options().get(TIMESTAMPS);
                    assertEquals(1, tsOpt.tsEcr);
                }

                // RFC 7323, Section 4.3, Situation B: holes in sequence space and filling these holes later
                // https://www.rfc-editor.org/rfc/rfc7323#section-4.3
                @Test
                void timestampFromTheLastSegmentThatAdvancesLeftWindowEdgeIsEchoed(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(1L);
                    when(clock.g()).thenReturn(0.001);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final RetransmissionQueue queue = new RetransmissionQueue(channel, 0, 201, true, clock);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 6001L, 4 * 1000, 100L, 201L, 4 * 1000, 200L, new SendBuffer(channel), queue, new ReceiveBuffer(channel), 1000 + SEG_HDR_SIZE);
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(Duration.ofMillis(100), ch -> null, false, null, ESTABLISHED, tcb, null, true, false);
                    channel.pipeline().addLast(handler);

                    Segment seg;
                    final ByteBuf data = Unpooled.buffer(1).writeBytes(randomBytes(1));
                    Segment response;
                    TimestampsOption tsOpt;

                    // <A, TSval=1> ------------------->
                    seg = Segment.ack(201, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(1, 0));
                    channel.writeInbound(seg);
                    assertEquals(1, queue.tsRecent);

                    // <---- <ACK(A), TSecr=1>
                    response = channel.readOutbound();
                    tsOpt = (TimestampsOption) response.options().get(TIMESTAMPS);
                    assertEquals(1, tsOpt.tsEcr);

                    // <A, TSval=3> ------------------->
                    seg = Segment.ack(203, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(3, 0));
                    channel.writeInbound(seg);
                    assertEquals(1, queue.tsRecent);

                    // <---- <ACK(A), TSecr=1>
                    response = channel.readOutbound();
                    tsOpt = (TimestampsOption) response.options().get(TIMESTAMPS);
                    assertEquals(1, tsOpt.tsEcr);

                    // <A, TSval=2> ------------------->
                    seg = Segment.ack(202, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(2, 0));
                    channel.writeInbound(seg);
                    assertEquals(2, queue.tsRecent);

                    // <---- <ACK(A), TSecr=2>
                    response = channel.readOutbound();
                    tsOpt = (TimestampsOption) response.options().get(TIMESTAMPS);
                    assertEquals(2, tsOpt.tsEcr);

                    // <A, TSval=5> ------------------->
                    seg = Segment.ack(205, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(5, 0));
                    channel.writeInbound(seg);
                    assertEquals(2, queue.tsRecent);

                    // <---- <ACK(A), TSecr=2>
                    response = channel.readOutbound();
                    tsOpt = (TimestampsOption) response.options().get(TIMESTAMPS);
                    assertEquals(2, tsOpt.tsEcr);

                    // <A, TSval=4> ------------------->
                    seg = Segment.ack(204, 310, data.copy());
                    seg.options().put(TIMESTAMPS, new TimestampsOption(4, 0));
                    channel.writeInbound(seg);
                    assertEquals(4, queue.tsRecent);

                    // <---- <ACK(A), TSecr=4>
                    response = channel.readOutbound();
                    tsOpt = (TimestampsOption) response.options().get(TIMESTAMPS);
                    assertEquals(4, tsOpt.tsEcr);
                }

                // RFC 7323, Appendix D, OPEN call
                // https://www.rfc-editor.org/rfc/rfc7323#appendix-D
                @Test
                void openCallSynShouldContainCorrectTsOption(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                        final long iss = 100;
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch, clock), new ReceiveBuffer(ch), 1200 + SEG_HDR_SIZE);
                    }, true, null, CLOSED, null, null, false, false);
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                    Segment actual = channel.readOutbound();

                    // RFC 7323
                    // on a OPEN call, the SYN must TSVal set to Snd.TSclock.
                    final TimestampsOption tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                    assertEquals(2816L, tsOpt.tsVal);
                    assertFalse(handler.tcb.retransmissionQueue.sndTsOk);
                    assertEquals(0, handler.tcb.retransmissionQueue.lastAckSent);

                    channel.close();
                }

                // RFC 7323, Appendix D, SEND call in LISTEN state
                // https://www.rfc-editor.org/rfc/rfc7323#appendix-D
                @Test
                void sendCallInListenStateSynShouldContainCorrectTsOption(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ZERO, ch -> {
                        final long iss = 100;
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch, clock), new ReceiveBuffer(ch), 1200 + SEG_HDR_SIZE);
                    }, false, null, CLOSED, null, null, false, false);
                    channel.pipeline().addLast(handler);

                    // write perform an active OPEN handshake
                    final ByteBuf data = Unpooled.buffer(3).writeBytes(randomBytes(3));
                    channel.writeOutbound(data);

                    final Segment actual = channel.readOutbound();
                    final TimestampsOption tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                    assertEquals(2816L, tsOpt.tsVal);
                    assertFalse(handler.tcb.retransmissionQueue.sndTsOk);
                    assertEquals(0, handler.tcb.retransmissionQueue.lastAckSent);

                    channel.close();
                }

                // RFC 7323, Appendix D, SEND call in ESTABLISHED state
                // https://www.rfc-editor.org/rfc/rfc7323#appendix-D
                @Test
                void sendCallInEstablishedStateSynShouldContainCorrectTsOption(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);

                    final int bytes = 300;
                    final int effSndMss = 100;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(100L, 100L, 1000, 100L, 300L, 1000, 300L, new SendBuffer(channel), new RetransmissionQueue(channel, clock, true, 123L), new ReceiveBuffer(channel), effSndMss + SEG_HDR_SIZE), null, true, false);
                    channel.pipeline().addLast(handler);

                    // as effSndMss is set to 100, the buf will be segmetized into 100 byte long segments. The last has the PSH flag set.
                    final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                    channel.writeOutbound(data);

                    for (int i = 0; i < bytes - effSndMss; i += effSndMss) {
                        final Segment actual = channel.readOutbound();
                        final TimestampsOption tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                        assertEquals(2816L, tsOpt.tsVal);
                        assertEquals(123L, tsOpt.tsEcr);
                    }

                    final Segment actual = channel.readOutbound();
                    final TimestampsOption tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                    assertEquals(2816L, tsOpt.tsVal);
                    assertEquals(123L, tsOpt.tsEcr);

                    channel.close();
                }

                // RFC 7323, Appendix D, SEGMENT ARRIVES on LISTEN state
                // https://www.rfc-editor.org/rfc/rfc7323#appendix-D
                @Test
                void segmentWithTimestampsArrivesOnListenState(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> {
                        final long iss = Segment.randomSeq();
                        return new TransmissionControlBlock(iss, iss, 0, iss, 0, 64_000, 0, new SendBuffer(ch), new RetransmissionQueue(ch, clock), new ReceiveBuffer(ch), 100 + SEG_HDR_SIZE);
                    }, false, null, LISTEN, null, null, true, false);
                    channel.pipeline().addLast(handler);

                    TimestampsOption tsOpt;

                    final Map<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
                    tsOpt = new TimestampsOption(1, 2);
                    options.put(TIMESTAMPS, tsOpt);
                    final Segment seg = Segment.syn(100, options);
                    channel.writeInbound(seg);

                    // Check for a TSopt option; if one is found, save SEG.TSval in the variable
                    // TS.Recent and turn on the Snd.TS.OK bit.
                    assertEquals(tsOpt.tsVal, handler.tcb.retransmissionQueue.tsRecent);
                    assertTrue(handler.tcb.retransmissionQueue.sndTsOk);

                    // If the Snd.TS.OK bit is on, include a
                    // TSopt <TSval=Snd.TSclock, TSecr=TS.Recent> in this segment. Last.ACK.sent is
                    // set to RCV.NXT.
                    final Segment actual = channel.readOutbound();
                    tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                    assertEquals(2816L, tsOpt.tsVal);
                    assertEquals(handler.tcb.retransmissionQueue.tsRecent, tsOpt.tsEcr);
                    assertEquals(handler.tcb.rcvNxt(), handler.tcb.retransmissionQueue.lastAckSent);

                    channel.close();
                }

                // RFC 7323, Appendix D, SEGMENT ARRIVES on SYN-SENT state
                // https://www.rfc-editor.org/rfc/rfc7323#appendix-D
                @Test
                void segmentWithTimestampsArrivesOnSynSentState(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, SYN_SENT, new TransmissionControlBlock(100L, 101L, 0, 100L, 0L, 64_000, 0, new SendBuffer(channel), new RetransmissionQueue(channel, clock), new ReceiveBuffer(channel), 100 + SEG_HDR_SIZE), null, true, false);
                    channel.pipeline().addLast(handler);

                    TimestampsOption tsOpt;

                    final Map<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
                    tsOpt = new TimestampsOption(1, 2);
                    options.put(TIMESTAMPS, tsOpt);
                    final Segment seg = Segment.synAck(999, 101, options);
                    channel.writeInbound(seg);

                    // Check for a TSopt option; if one is found, save SEG.TSval in the variable
                    // TS.Recent and turn on the Snd.TS.OK bit.
                    assertEquals(tsOpt.tsVal, handler.tcb.retransmissionQueue.tsRecent);
                    assertTrue(handler.tcb.retransmissionQueue.sndTsOk);
                    // If the ACK bit is set, use Snd.TSclock - SEG.TSecr as the initial RTT estimate.
                    assertEquals(8442, handler.tcb.retransmissionQueue.rto());

                    // If the Snd.TS.OK bit is on, include a
                    // TSopt <TSval=Snd.TSclock, TSecr=TS.Recent> in this segment. Last.ACK.sent is
                    // set to RCV.NXT.
                    final Segment actual = channel.readOutbound();
                    tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                    assertEquals(2816L, tsOpt.tsVal);
                    assertEquals(handler.tcb.retransmissionQueue.tsRecent, tsOpt.tsEcr);
                    assertEquals(handler.tcb.rcvNxt(), handler.tcb.retransmissionQueue.lastAckSent);

                    channel.close();
                }

                // RFC 7323, Appendix D, SEGMENT ARRIVES on other state
                // https://www.rfc-editor.org/rfc/rfc7323#appendix-D
                @Test
                void segmentWithTimestampsArrivesOnOtherState(@Mock final Clock clock) {
                    when(clock.time()).thenReturn(2816L);

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(100L, 101L, 0, 100L, 0L, 64_000, 0, new SendBuffer(channel), new RetransmissionQueue(channel, clock, true, 0), new ReceiveBuffer(channel), 100 + SEG_HDR_SIZE), null, true, false);
                    channel.pipeline().addLast(handler);

                    TimestampsOption tsOpt;

                    final Map<SegmentOption, Object> options = new EnumMap<>(SegmentOption.class);
                    tsOpt = new TimestampsOption(1, 2);
                    options.put(TIMESTAMPS, tsOpt);
                    final Segment seg = Segment.ack(0, 102, options);
                    channel.writeInbound(seg);

                    // Check for a TSopt option; if one is found, save SEG.TSval in the variable
                    // TS.Recent and turn on the Snd.TS.OK bit.
                    assertEquals(tsOpt.tsVal, handler.tcb.retransmissionQueue.tsRecent);
                    assertTrue(handler.tcb.retransmissionQueue.sndTsOk);
                    // If the ACK bit is set, use Snd.TSclock - SEG.TSecr as the initial RTT estimate.
                    assertEquals(1000, handler.tcb.retransmissionQueue.rto());

                    // If the Snd.TS.OK bit is on, include a
                    // TSopt <TSval=Snd.TSclock, TSecr=TS.Recent> in this segment. Last.ACK.sent is
                    // set to RCV.NXT.
                    final Segment actual = channel.readOutbound();
                    tsOpt = (TimestampsOption) actual.options().get(TIMESTAMPS);
                    assertEquals(2816L, tsOpt.tsVal);
                    assertEquals(handler.tcb.retransmissionQueue.tsRecent, tsOpt.tsEcr);
                    assertEquals(handler.tcb.rcvNxt(), handler.tcb.retransmissionQueue.lastAckSent);

                    channel.close();
                }
            }
        }
    }

    @Nested
    class UserCallSend {
        @Test
        void shouldRejectOutboundNonByteBufs() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
            channel.pipeline().addLast(handler);

            assertThrows(UnsupportedMessageTypeException.class, () -> channel.writeOutbound("Hello World"));

            channel.close();
        }

        @Test
        void shouldRejectOutboundDataIfConnectionIsClosed() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
            channel.pipeline().addLast(handler);

            handler.state = CLOSED;
            handler.tcb = null;

            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            assertThrows(ClosedChannelException.class, () -> channel.writeOutbound(buf));

            channel.close();
        }

        @Test
        void shouldEnqueueDataIfConnectionEstablishmentIsStillInProgress() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, SYN_SENT, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
            channel.pipeline().addLast(handler);

            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            channel.write(buf);

            channel.close();
        }

        @Test
        void shouldEnqueueDataIfConnectionEstablishmentIsStillInProgress2() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, SYN_RECEIVED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
            channel.pipeline().addLast(handler);

            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            assertFalse(channel.writeOutbound(buf));

            buf.release();
            channel.close();
        }

        // FIXME: test bauen wenn peer nicht auf CLOSE antwortet. War früher das hier, oder? https://github.com/drasyl/drasyl/blob/master/drasyl-extras/src/test/java/org/drasyl/handler/connection/ConnectionHandshakeHandlerTest.java#L347
        // FIXME: gleichen test für SYN?
        // FIXME: gleichen test für jeden state? :P

        // FIXME: wann clearen wir unsere write queue?

        @Test
        void shouldFailWriteIfChannelIsClosing() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, FIN_WAIT_1, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), 1220 + SEG_HDR_SIZE), null, true, false);
            channel.pipeline().addLast(handler);

            final ByteBuf data = Unpooled.buffer(3).writeBytes(randomBytes(3));
            assertThrows(ConnectionHandshakeException.class, () -> channel.writeOutbound(data));
        }
    }

    @Nested
    class UserCallClose {
//        @Test
//        void name() {
//            final EmbeddedChannel channel = new EmbeddedChannel();
//            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, 100, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE));
//            channel.pipeline().addLast(handler);
//
//            final ChannelFuture future = channel.pipeline().close();
//        }
    }

    @Nested
    class SegmentArrives {
        @Nested
        class OnSynSentState {
            @Test
            void shouldResetConnectionOnResetSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, SYN_SENT, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(Segment.rstAck(1, 101)));

                channel.close();
            }
        }

        @Nested
        class OnSynReceivedState {
            @Test
            void shouldCloseConnectionIfPeerResetsConnectionAndWeAreInActiveOpenMode() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, true, null, SYN_RECEIVED, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(Segment.rstAck(100, 101)));

                channel.close();
            }

            @Test
            void shouldReturnToListenStateIfPeerResetsConnectionAndWeAreInPassiveOpenMode() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, SYN_RECEIVED, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                channel.writeInbound(Segment.rstAck(100, 101));
                assertEquals(LISTEN, handler.state);

                channel.close();
            }

            @Test
            @Disabled
            void shouldResetConnectionIfPeerSentNotAcceptableSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, true, null, SYN_RECEIVED, new TransmissionControlBlock(channel, 101L, 101L, 100L, 100L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, false, false);
                channel.pipeline().addLast(handler);

                channel.writeInbound(Segment.ack(100, 101));
                assertEquals(Segment.rst(101), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnEstablishedState {
            @Test
            void shouldPassReceivedContentWhenConnectionIsEstablished() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 110L, 111L, 100L, 50L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(Segment.pshAck(50, 110, data));
                assertEquals(data, channel.readInbound());

                channel.close();

                data.release();
            }

            @Test
            void shouldIgnoreSegmentWithDuplicateAck() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 110L, 111L, 100L, 50L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                channel.writeInbound(Segment.ack(50, 109));
                assertNull(channel.readOutbound());

                channel.close();
            }

            @Test
            void shouldReplyWithExpectedAckIfWeGotAckSomethingNotYetSent() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 110L, 111L, 100L, 50L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(Segment.pshAck(50, 200, data));
                assertEquals(Segment.ack(111, 50), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnClosingState {
            @Test
            void shouldCloseConnectionOnResetSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, true, null, CLOSING, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                channel.writeInbound(Segment.rstAck(100, 101));
                assertFalse(channel.isOpen());

                channel.close();
            }
        }

        @Nested
        class OnLastAckState {
            @Test
            void shouldCloseConnectionOnResetSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, true, null, CLOSING, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                channel.writeInbound(Segment.rstAck(100, 101));
                assertFalse(channel.isOpen());

                channel.close();
            }
        }

        @Nested
        class OnClosedState {
            @Test
            void shouldIgnoreResetSegmentsWhenConnectionIsClosed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                handler.state = CLOSED;

                final Segment seg = Segment.rst(1);
                channel.writeInbound(seg);
                assertNull(channel.readOutbound());

                channel.close();
            }

            @Test
            void shouldReplyWithResetWhenConnectionIsClosed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                handler.state = CLOSED;

                final Segment seg = Segment.syn(123, 64_000);
                channel.writeInbound(seg);
                assertEquals(Segment.rstAck(0, 124, 1_000), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnAnyState {
            @Test
            void shouldRejectInboundNonByteBufs() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ReliableDeliveryHandler handler = new ReliableDeliveryHandler(ofMillis(100), ch -> null, false, null, ESTABLISHED, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100 + SEG_HDR_SIZE), null, true, false);
                channel.pipeline().addLast(handler);

                final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(buf);

                assertNull(channel.readInbound());
                assertEquals(0, buf.refCnt());

                channel.close();
            }
        }
    }
}
