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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMillis;
import static org.awaitility.Awaitility.await;
import static org.drasyl.handler.connection.State.CLOSED;
import static org.drasyl.handler.connection.State.CLOSING;
import static org.drasyl.handler.connection.State.ESTABLISHED;
import static org.drasyl.handler.connection.State.FIN_WAIT_1;
import static org.drasyl.handler.connection.State.FIN_WAIT_2;
import static org.drasyl.handler.connection.State.LAST_ACK;
import static org.drasyl.handler.connection.State.LISTEN;
import static org.drasyl.handler.connection.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.State.SYN_SENT;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConnectionHandshakeHandlerTest {
    @Test
    void exceptionsShouldCloseTheConnection(@Mock final Throwable cause) {
        final EmbeddedChannel channel = new EmbeddedChannel();
        final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
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
                // active client, passive server
                @Test
                void clientShouldSynchronizeWhenServerBehavesExpectedly() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, true, CLOSED, 1200, 64_000, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                    Object actual = channel.readOutbound();
                    assertEquals(ConnectionHandshakeSegment.syn(100, 64_000), actual);
                    assertEquals(SYN_SENT, handler.state);

                    assertEquals(100, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(0, handler.tcb.rcvNxt());

                    // peer SYNchronizes his SEG with us and ACKed our segment, we reply with ACK for his SYN
                    channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101, 64_000));
                    assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
                    assertEquals(ESTABLISHED, handler.state);

                    assertEquals(101, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(301, handler.tcb.rcvNxt());

                    assertTrue(channel.isOpen());
                    channel.close();
                }

                // active client, active server
                // Both peers are in CLOSED state
                // Both peers initiate handshake simultaneous
                @Test
                void clientShouldSynchronizeIfServerPerformsSimultaneousHandshake() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, true, CLOSED, 1200, 64_000, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                    assertEquals(ConnectionHandshakeSegment.syn(100, 64_000), channel.readOutbound());
                    assertEquals(SYN_SENT, handler.state);

                    assertEquals(100, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(0, handler.tcb.rcvNxt());

                    // peer SYNchronizes his SEG before our SYN has been received
                    channel.writeInbound(ConnectionHandshakeSegment.syn(300, 64_000));
                    assertEquals(SYN_RECEIVED, handler.state);
                    assertEquals(ConnectionHandshakeSegment.synAck(100, 301, 64_000), channel.readOutbound());

                    assertEquals(100, handler.tcb.sndUna());
                    assertEquals(101, handler.tcb.sndNxt());
                    assertEquals(301, handler.tcb.rcvNxt());

                    // peer respond to our SYN with ACK (and another SYN)
                    channel.writeInbound(ConnectionHandshakeSegment.synAck(301, 101, 64_000));
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
                @Test
                void passiveServerShouldSynchronizeWhenClientBehavesExpectedly() {
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 300, false, CLOSED, 1200, 64_000, new TransmissionControlBlock(300, 300, 1220 * 64, 300, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should change state to LISTEN
                    assertEquals(LISTEN, handler.state);

                    // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
                    channel.writeInbound(ConnectionHandshakeSegment.syn(100, 64_000));
                    assertEquals(ConnectionHandshakeSegment.synAck(300, 101, 64_000), channel.readOutbound());
                    assertEquals(SYN_RECEIVED, handler.state);

                    assertEquals(300, handler.tcb.sndUna());
                    assertEquals(301, handler.tcb.sndNxt());
                    assertEquals(101, handler.tcb.rcvNxt());

                    // peer ACKed our SYN
                    // we piggyback some data, that should also be processed by the server
                    final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                    channel.writeInbound(ConnectionHandshakeSegment.pshAck(101, 301, data));
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
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, CLOSED, 1200, 64_000, new TransmissionControlBlock(100, 100, 1000 * 64, 100, 0, 1000 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                    channel.pipeline().addLast(handler);

                    // handlerAdded on active channel should change state to LISTEN
                    assertEquals(LISTEN, handler.state);

                    // write should perform an active OPEN handshake
                    final ByteBuf data = Unpooled.buffer(3).writeBytes(randomBytes(3));
                    channel.writeOutbound(data);
                    assertEquals(ConnectionHandshakeSegment.syn(100, 64_000), channel.readOutbound());
                    assertEquals(SYN_SENT, handler.state);

                    // after handshake the write should be formed
                    channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101, 64_000));
                    assertEquals(ESTABLISHED, handler.state);
                    assertEquals(ConnectionHandshakeSegment.pshAck(101, 301, data), channel.readOutbound());

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
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, true, CLOSED, 1200, 64_000, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
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
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 300, false, CLOSED, 1200, 64_000, new TransmissionControlBlock(300, 300, 1220 * 64, 300, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                    channel.pipeline().addLast(handler);

                    // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
                    channel.writeInbound(ConnectionHandshakeSegment.syn(100, 64_000));

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
            // we've crashed
            // peer is in ESTABLISHED state
            @Test
            void weShouldResetPeerIfWeHaveDiscoveredThatWeHaveCrashed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 400, true, CLOSED, 1200, 64_000, new TransmissionControlBlock(400, 400, 1220 * 64, 400, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                channel.pipeline().addLast(handler);

                // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
                assertEquals(ConnectionHandshakeSegment.syn(400, 64_000), channel.readOutbound());
                assertEquals(SYN_SENT, handler.state);

                assertEquals(400, handler.tcb.sndUna());
                assertEquals(401, handler.tcb.sndNxt());
                assertEquals(0, handler.tcb.rcvNxt());

                // as we got an ACK for an unexpected seq, reset the peer
                channel.writeInbound(ConnectionHandshakeSegment.ack(300, 100));
                assertEquals(ConnectionHandshakeSegment.rst(100), channel.readOutbound());
                assertEquals(SYN_SENT, handler.state);

                assertEquals(400, handler.tcb.sndUna());
                assertEquals(401, handler.tcb.sndNxt());
                assertEquals(0, handler.tcb.rcvNxt());

                assertTrue(channel.isOpen());
                channel.close();
            }

            // we're in ESTABLISHED state
            // peer has crashed
            @Test
            void weShouldCloseOurConnectionIfPeerHasDiscoveredThatPeerHasCrashed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, true, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(300L, 300L, 1220 * 64, 300L, 100L, 1220 * 64, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                channel.pipeline().addLast(handler);

                // other wants to SYNchronize with us, ACK with our expected seq
                channel.writeInbound(ConnectionHandshakeSegment.syn(400, 64_000));
                assertEquals(ESTABLISHED, handler.state);
                assertEquals(ConnectionHandshakeSegment.ack(300, 100), channel.readOutbound());

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(300, handler.tcb.sndNxt());
                assertEquals(100, handler.tcb.rcvNxt());

                // as we sent an ACK for an unexpected seq, peer will reset us
                final ConnectionHandshakeSegment msg = ConnectionHandshakeSegment.rst(100);
                assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(msg));
                assertEquals(CLOSED, handler.state);
                assertNull(handler.tcb);
            }
        }

        @Nested
        class AbortedSynchronization {
            @Test
            void shouldCancelOpenCallAndCloseChannel() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(1_000), () -> 100, true, CLOSED, 1200, 64_000, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                channel.pipeline().addLast(handler);

                channel.pipeline().close();
            }
        }
    }

    @Nested
    class ConnectionClearing {
        @Nested
        class SuccessfulClearing {
            // Both peers are in ESTABLISHED state
            // we close
            @Test
            void weShouldPerformNormalCloseSequenceOnChannelClose() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(100L, 100L, 1220 * 64, 100L, 300L, 1220 * 64, 300L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.pipeline().close();
                assertEquals(FIN_WAIT_1, handler.state);
                assertEquals(ConnectionHandshakeSegment.finAck(100, 300), channel.readOutbound());
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // my close got ACKed
                channel.writeInbound(ConnectionHandshakeSegment.ack(300, 101));
                assertEquals(FIN_WAIT_2, handler.state);
                assertFalse(future.isDone());

                assertEquals(101, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // peer now triggers close as well
                channel.writeInbound(ConnectionHandshakeSegment.finAck(300, 101));
                assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
                assertEquals(CLOSED, handler.state);
                assertTrue(future.isDone());

                // FIXME: wieder einbauen?
                //assertNull(handler.tcb);
            }

            // Both peers are in ESTABLISHED state
            // other peer close
            @Test
            void weShouldPerformNormalCloseSequenceWhenPeerInitiateClose() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(299L, 300L, 1220 * 64, 300L, 100L, 1220 * 64, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                channel.pipeline().addLast(handler);

                // peer triggers close
                channel.writeInbound(ConnectionHandshakeSegment.finAck(100, 300));

                // we should trigger a close as well
                assertEquals(LAST_ACK, handler.state);
                assertEquals(ConnectionHandshakeSegment.finAck(300, 101), channel.readOutbound());

                assertEquals(300, handler.tcb.sndUna());
                assertEquals(301, handler.tcb.sndNxt());
                assertEquals(101, handler.tcb.rcvNxt());

                // peer ACKed our close
                channel.writeInbound(ConnectionHandshakeSegment.ack(101, 301));

                assertEquals(CLOSED, handler.state);
                assertNull(handler.tcb);
            }

            // Both peers are in ESTABLISHED state
            // Both peers initiate close simultaneous
            @Test
            void weShouldPerformSimultaneousCloseIfBothPeersInitiateACloseAtTheSameTime() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(100L, 100L, 1220 * 64, 100L, 300L, 1220 * 64, 300L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
                channel.pipeline().addLast(handler);

                // trigger close
                final ChannelFuture future = channel.close();
                assertEquals(FIN_WAIT_1, handler.state);
                assertEquals(ConnectionHandshakeSegment.finAck(100, 300), channel.readOutbound());
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(300, handler.tcb.rcvNxt());

                // got parallel close
                channel.writeInbound(ConnectionHandshakeSegment.finAck(300, 100));
                assertEquals(CLOSING, handler.state);
                assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
                assertFalse(future.isDone());

                assertEquals(100, handler.tcb.sndUna());
                assertEquals(101, handler.tcb.sndNxt());
                assertEquals(301, handler.tcb.rcvNxt());

                channel.writeInbound(ConnectionHandshakeSegment.ack(301, 101));

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
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
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
            // FIXME: ist das überhaupt teil des handlers oder eher TCB?
            final EmbeddedChannel channel = new EmbeddedChannel();
            TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 301L, 1000, 100L, 100, 100L, 1000);
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
            channel.pipeline().addLast(handler);

            final ByteBuf receivedData = Unpooled.buffer(100).writeBytes(randomBytes(100));
            channel.writeInbound(ConnectionHandshakeSegment.ack(100, 301L, 1000, receivedData));

            final ByteBuf dataPassedToApplication = channel.readInbound();
            assertEquals(receivedData, dataPassedToApplication);

            channel.close();
        }

        @Nested
        class Mss {
            @Test
            void shouldSegmentizeDataIntoSegmentsToLargerThenMss() {
                // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                final int bytes = 250;
                final int mss = 100;

                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, mss));
                channel.pipeline().addLast(handler);

                // as mss is set to 100, the buf will be segmetized into 100 byte long segments. The last has the PSH flag set.
                final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                channel.writeOutbound(data);

                for (int i = 0; i < bytes - mss; i += mss) {
                    assertEquals(ConnectionHandshakeSegment.ack(100 + i, 300, data.slice(i, 100)), channel.readOutbound());
                }
                assertEquals(ConnectionHandshakeSegment.pshAck(300, 300, data.slice(200, 50)), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class Window {
            @Nested
            class SendWindow {
                @Test
                void senderShouldRespectSndWndWhenWritingToNetwork() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final int bytes = 600;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 100L, 300L, 1000, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    // no data in flight, everything should be written to network
                    final ByteBuf data1 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                    channel.writeOutbound(data1);
                    assertEquals(ConnectionHandshakeSegment.pshAck(100, 300, data1), channel.readOutbound());

                    // 600 bytes in flight, just 400 bytes allowed
                    final ByteBuf data2 = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                    channel.writeOutbound(data2);
                    assertEquals(ConnectionHandshakeSegment.pshAck(700, 300, data2.slice(0, 400)), channel.readOutbound());

                    // send ack for the first segment. The remaining 200 bytes should then be sent
                    channel.writeInbound(ConnectionHandshakeSegment.ack(300, 700, 600));
                    assertEquals(ConnectionHandshakeSegment.pshAck(1100, 300, data2.slice(400, 200)), channel.readOutbound());

                    channel.close();
                }

                @Test
                void zeroWindowProbing() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 0, 100L, 1000, 100L, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeOutbound(data);

                    // SND.WND is 0, we have to perform Zero-Window Probing
                    assertEquals(ConnectionHandshakeSegment.pshAck(600, 100, data.slice(0, 1)), channel.readOutbound());

                    channel.close();
                }

                @Test
                void senderShouldHandleSentSegmentsToBeAcknowledgedJustPartially() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(300L, 600L, 0, 100L, 100L, 1000, 100L, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    // 300 bytes in flight, only first 100 are ACKed
                    channel.writeInbound(ConnectionHandshakeSegment.ack(100, 400));

                    assertEquals(400, tcb.sndUna());
                    assertEquals(600, tcb.sndNxt());

                    channel.close();
                }
            }

            @Nested
            class ReceiveWindow {
                @Test
                void receiverShouldUpdateRcvWnd() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final int bytes = 600;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 1000, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    // initial value
                    assertEquals(1000, tcb.rcvWnd());

                    // 600 bytes added to RCV.BUF
                    final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                    channel.writeInbound(ConnectionHandshakeSegment.ack(100, 600, data));

                    assertEquals(400, tcb.rcvWnd());

                    // RCV.BUF flushed
                    channel.read();
                    assertEquals(1000, tcb.rcvWnd());

                    channel.close();
                }

                @Test
                void shouldOnlyAcceptAsManyBytesAsSpaceAvailableInReceiveBuffer() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 301L, 1000, 100L, 60, 100L, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    // we got more than we are willing to accept
                    final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeInbound(ConnectionHandshakeSegment.ack(100, 301L, 1000, data));

                    // we ACK just the part we have accepted
                    assertEquals(channel.readOutbound(), ConnectionHandshakeSegment.ack(301, 160));

                    channel.close();
                }

                // FIXME: Wir erhalten ACK welcher nur ein Teil eines SEG in unserer RetransmissionQueue bestätigt. RetransmissionQueue muss bestätigten Teil entfernen und rest neu packen
                @Disabled
                @Test
                void receiverShouldAbleToAckSegmentWhichContainsOnlyPartiallyNewSegments() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final EmbeddedChannel channel = new EmbeddedChannel();
                    channel.config().setAutoRead(false);
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 301L, 1000, 100L, 60, 100L, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    // we got more than we are willing to accept
                    final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeInbound(ConnectionHandshakeSegment.ack(100, 301L, 1000, data));

                    // we ACK just the part we have accepted
                    assertEquals(channel.readOutbound(), ConnectionHandshakeSegment.ack(301, 160));

                    // SEG not fully ACKed, we send again
                    final ByteBuf data2 = Unpooled.buffer(100).writeBytes(randomBytes(100));
                    channel.writeInbound(ConnectionHandshakeSegment.ack(100, 301L, 1000, data2));

                    channel.close();
                }

                // can be caused by a lost SEG
                @Test
                void receiverShouldRespondWithExpectedSegToUnexpectedSeg() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final int bytes = 600;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 1000, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    // SEG 100 is expected, but we send next SEG 400
                    final ByteBuf data = Unpooled.buffer(bytes).writeBytes(randomBytes(bytes));
                    channel.writeInbound(ConnectionHandshakeSegment.ack(400, 600, data));

                    // we get ACK with expected SEG 100 number
                    assertEquals(ConnectionHandshakeSegment.ack(600, 100), channel.readOutbound());

                    channel.close();
                }

                // can be caused by a lost SEG
                @Test
                void receiverShouldRespondWithExpectedSegToUnexpectedSeg2() {
                    // FIXME: ist das überhaupt teil des handlers oder eher TCB?
                    final int bytes = 600;

                    final EmbeddedChannel channel = new EmbeddedChannel();
                    TransmissionControlBlock tcb = new TransmissionControlBlock(channel, 300L, 600L, 100L, 100L, 1000, 1000);
                    final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, tcb.mss(), 64_000, tcb);
                    channel.pipeline().addLast(handler);

                    channel.writeInbound(ConnectionHandshakeSegment.ack(100, 300, 1000));

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
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
            channel.pipeline().addLast(handler);

            assertThrows(UnsupportedMessageTypeException.class, () -> channel.writeOutbound("Hello World"));

            channel.close();
        }

        @Test
        void shouldRejectOutboundDataIfConnectionIsClosed() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
            channel.pipeline().addLast(handler);

            handler.state = CLOSED;

            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            assertThrows(ClosedChannelException.class, () -> channel.writeOutbound(buf));

            channel.close();
        }

        @Test
        void shouldEnqueueDataIfConnectionEstablishmentIsStillInProgress() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, SYN_SENT, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
            channel.pipeline().addLast(handler);

            final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
            channel.write(buf);

            channel.close();
        }

        @Test
        void shouldEnqueueDataIfConnectionEstablishmentIsStillInProgress2() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, SYN_RECEIVED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
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
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, FIN_WAIT_1, 1200, 64_000, new TransmissionControlBlock(100, 100, 1220 * 64, 100, 0, 1220 * 64, 0, new SendBuffer(channel), new RetransmissionQueue(channel), new ReceiveBuffer(channel), new RttMeasurement(), 1220));
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
//            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, 100, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
//            channel.pipeline().addLast(handler);
//
//            final ChannelFuture future = channel.pipeline().close();
//        }
    }

    @Nested
    class SegmentArrives {
        @Nested
        class OnListenState {
            @Test
            void shouldIgnoreResetAsThereIsNothingToReset() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, LISTEN, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
                channel.pipeline().addLast(handler);

                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.ack(100, 123);
                channel.writeInbound(seg);
                assertEquals(ConnectionHandshakeSegment.rst(123), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnSynSentState {
            @Test
            void shouldResetConnectionOnResetSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, SYN_SENT, 100, 64_000, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(ConnectionHandshakeSegment.rstAck(1, 101)));

                channel.close();
            }
        }

        @Nested
        class OnSynReceivedState {
            @Test
            void shouldCloseConnectionIfPeerResetsConnectionAndWeAreInActiveOpenMode() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, true, SYN_RECEIVED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(ConnectionHandshakeSegment.rstAck(100, 101)));

                channel.close();
            }

            @Test
            void shouldReturnToListenStateIfPeerResetsConnectionAndWeAreInPassiveOpenMode() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, SYN_RECEIVED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                channel.writeInbound(ConnectionHandshakeSegment.rstAck(100, 101));
                assertEquals(LISTEN, handler.state);

                channel.close();
            }

            @Test
            @Disabled
            void shouldResetConnectionIfPeerSentNotAcceptableSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, true, SYN_RECEIVED, 100, 64_000, new TransmissionControlBlock(channel, 101L, 101L, 100L, 100L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                channel.writeInbound(ConnectionHandshakeSegment.ack(100, 101));
                assertEquals(ConnectionHandshakeSegment.rst(101), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnEstablishedState {
            @Test
            void shouldPassReceivedContentWhenConnectionIsEstablished() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(channel, 110L, 111L, 100L, 50L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(ConnectionHandshakeSegment.pshAck(50, 110, data));
                assertEquals(data, channel.readInbound());

                channel.close();

                data.release();
            }

            @Test
            void shouldIgnoreSegmentWithDuplicateAck() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(channel, 110L, 111L, 100L, 50L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                channel.writeInbound(ConnectionHandshakeSegment.ack(50, 109));
                assertNull(channel.readOutbound());

                channel.close();
            }

            @Test
            void shouldReplyWithExpectedAckIfWeGotAckSomethingNotYetSent() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 1200, 64_000, new TransmissionControlBlock(channel, 110L, 111L, 100L, 50L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(ConnectionHandshakeSegment.pshAck(50, 200, data));
                assertEquals(ConnectionHandshakeSegment.ack(111, 50), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnClosingState {
            @Test
            void shouldCloseConnectionOnResetSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, true, CLOSING, 100, 64_000, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                channel.writeInbound(ConnectionHandshakeSegment.rstAck(100, 101));
                assertFalse(channel.isOpen());

                channel.close();
            }
        }

        @Nested
        class OnLastAckState {
            @Test
            void shouldCloseConnectionOnResetSegment() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, true, CLOSING, 100, 64_000, new TransmissionControlBlock(channel, 100L, 101L, 100L, 100L, 1220 * 64, 1220));
                channel.pipeline().addLast(handler);

                channel.writeInbound(ConnectionHandshakeSegment.rstAck(100, 101));
                assertFalse(channel.isOpen());

                channel.close();
            }
        }

        @Nested
        class OnClosedState {
            @Test
            void shouldIgnoreResetSegmentsWhenConnectionIsClosed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
                channel.pipeline().addLast(handler);

                handler.state = CLOSED;

                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.rst(1);
                channel.writeInbound(seg);
                assertNull(channel.readOutbound());

                channel.close();
            }

            @Test
            void shouldReplyWithResetWhenConnectionIsClosed() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
                channel.pipeline().addLast(handler);

                handler.state = CLOSED;

                final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.syn(123, 64_000);
                channel.writeInbound(seg);
                assertEquals(ConnectionHandshakeSegment.rstAck(0, 123), channel.readOutbound());

                channel.close();
            }
        }

        @Nested
        class OnAnyState {
            @Test
            void shouldRejectInboundNonByteBufs() {
                final EmbeddedChannel channel = new EmbeddedChannel();
                final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
                channel.pipeline().addLast(handler);

                final ByteBuf buf = Unpooled.buffer(10).writeBytes(randomBytes(10));
                channel.writeInbound(buf);

                assertNull(channel.readInbound());
                assertEquals(0, buf.refCnt());

                channel.close();
            }
        }
    }

    @Nested
    class RetransmissionTimeout {
        @Test
        void shouldCreateRetransmissionTimerIfAcknowledgeableSegmentIsSent() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(50), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
            channel.pipeline().addLast(handler);

            final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
            channel.writeOutbound(data);

            final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.pshAck(100, 300, data);
            assertEquals(seg, channel.readOutbound());

            // retransmission timer should send segment again
            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(seg, channel.readOutbound());
            });

            channel.close();
        }

        @Test
        void shouldCancelRetransmissionTimerIfSentAcknowledgeableSegmentHasBeenAcknowledged() throws InterruptedException {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(50), () -> 100, false, ESTABLISHED, 100, 64_000, new TransmissionControlBlock(channel, 100L, 300L, 1000, 100));
            channel.pipeline().addLast(handler);

            final ByteBuf data = Unpooled.buffer(100).writeBytes(randomBytes(100));
            channel.writeOutbound(data);

            final ConnectionHandshakeSegment seg = ConnectionHandshakeSegment.pshAck(100, 300, data);
            assertEquals(seg, channel.readOutbound());

            channel.writeInbound(ConnectionHandshakeSegment.ack(300, 200));

            // as retransmission has been canceled, no segment should be sent again
            Thread.sleep(100);
            channel.runScheduledPendingTasks();
            assertNull(channel.readOutbound());

            channel.close();
        }
    }
}
