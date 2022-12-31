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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ZERO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConnectionHandshakeHandlerTest {
    // "Server" is in CLOSED state
    // "Client" initiate handshake
    @Nested
    class ConnectionEstablishment {
        @Test
        void clientShouldSynchronizeWhenServerBehavesExpectedly() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, true, CLOSED, 1200, new TransmissionControlBlock(channel, 100));
            channel.pipeline().addLast(handler);

            // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
            Object actual = channel.readOutbound();
            assertEquals(ConnectionHandshakeSegment.syn(100), actual);
            assertEquals(SYN_SENT, handler.state);

            assertEquals(100, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(0, handler.tcb().rcvNxt);

            // peer SYNchronizes his SEG with us and ACKed our segment, we reply with ACK for his SYN
            channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101));
            assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(101, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(301, handler.tcb().rcvNxt);

            assertTrue(channel.isOpen());
            channel.close();
        }

        @Test
        void passiveServerShouldSynchronizeWhenClientBehavesExpectedly() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 300, false, CLOSED, 1200, new TransmissionControlBlock(channel, 300));
            channel.pipeline().addLast(handler);

            // handlerAdded on active channel should change state to LISTEN
            assertEquals(LISTEN, handler.state);

            // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
            channel.writeInbound(ConnectionHandshakeSegment.syn(100));
            assertEquals(ConnectionHandshakeSegment.synAck(300, 101), channel.readOutbound());
            assertEquals(SYN_RECEIVED, handler.state);

            assertEquals(300, handler.tcb().sndUna);
            assertEquals(301, handler.tcb().sndNxt);
            assertEquals(101, handler.tcb().rcvNxt);

            // peer ACKed our SYN
            // we piggyback some data, that should also be processed by the server
            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            channel.writeInbound(ConnectionHandshakeSegment.pshAck(101, 301, data));
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(301, handler.tcb().sndUna);
            assertEquals(301, handler.tcb().sndNxt);
            assertEquals(111, handler.tcb().rcvNxt);
            assertEquals(data, channel.readInbound());

            assertTrue(channel.isOpen());
            channel.close();
            data.release();
        }

        // Both peers are in CLOSED state
        // Both peers initiate handshake simultaneous
        @Test
        void clientShouldSynchronizeIfServerPerformsSimultaneousHandshake() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, true, CLOSED, 1200, new TransmissionControlBlock(channel, 100));
            channel.pipeline().addLast(handler);

            // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
            assertEquals(ConnectionHandshakeSegment.syn(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertEquals(100, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(0, handler.tcb().rcvNxt);

            // peer SYNchronizes his SEG before our SYN has been received
            channel.writeInbound(ConnectionHandshakeSegment.syn(300));
            assertEquals(SYN_RECEIVED, handler.state);
            assertEquals(ConnectionHandshakeSegment.synAck(100, 301), channel.readOutbound());

            assertEquals(100, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(301, handler.tcb().rcvNxt);

            // peer respond to our SYN with ACK (and another SYN)
            channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101));
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(101, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(302, handler.tcb().rcvNxt);

            assertTrue(channel.isOpen());
            channel.close();
        }

        // half-open discovery
        // we've crashed
        // peer is in ESTABLISHED state
        @Test
        void weShouldResetPeerIfWeHaveDiscoveredThatWeHaveCrashed() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 400, true, CLOSED, 1200, new TransmissionControlBlock(channel, 400));
            channel.pipeline().addLast(handler);

            // handlerAdded on active channel should trigger SYNchronize of our SEG with peer
            assertEquals(ConnectionHandshakeSegment.syn(400), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertEquals(400, handler.tcb().sndUna);
            assertEquals(401, handler.tcb().sndNxt);
            assertEquals(0, handler.tcb().rcvNxt);

            // as we got an ACK for an unexpected seq, reset the peer
            channel.writeInbound(ConnectionHandshakeSegment.ack(300, 100));
            assertEquals(ConnectionHandshakeSegment.rst(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertEquals(400, handler.tcb().sndUna);
            assertEquals(401, handler.tcb().sndNxt);
            assertEquals(0, handler.tcb().rcvNxt);

            assertTrue(channel.isOpen());
            channel.close();
        }

        // half-open discovery
        // we're in ESTABLISHED state
        // peer has crashed
        @Test
        void weShouldCloseOurConnectionIfPeerHasDiscoveredThatPeerHasCrashed() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, true, ESTABLISHED, 1200, new TransmissionControlBlock(channel, 299L, 100L));
            channel.pipeline().addLast(handler);

            // other wants to SYNchronize with us, ACK with our expected seq
            channel.writeInbound(ConnectionHandshakeSegment.syn(400));
            assertEquals(ESTABLISHED, handler.state);
            assertEquals(ConnectionHandshakeSegment.ack(300, 100), channel.readOutbound());

            assertEquals(299, handler.tcb().sndUna);
            assertEquals(300, handler.tcb().sndNxt);
            assertEquals(100, handler.tcb().rcvNxt);

            // as we sent an ACK for an unexpected seq, peer will reset us
            final ConnectionHandshakeSegment msg = ConnectionHandshakeSegment.rst(100);
            assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(msg));
            assertEquals(CLOSED, handler.state);

            assertEquals(299, handler.tcb().sndUna);
            assertEquals(300, handler.tcb().sndNxt);
            assertEquals(100, handler.tcb().rcvNxt);
        }

        // dead peer
        // server is not responding to the SYN
        @Test
        void clientShouldCloseChannelIfServerIsNotRespondingToSyn() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, true, CLOSED, 1200, new TransmissionControlBlock(channel, 100));
            channel.pipeline().addLast(handler);

            // peer is dead and therefore no SYN/ACK is received
            // wait for user timeout
            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(CLOSED, handler.state);
            });

            assertFalse(channel.isOpen());
        }

        // dead peer
        // client is not responding to the SYN/ACK
        @Test
        void serverShouldCloseChannelIfClientIsNotRespondingToSynAck() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 300, false, CLOSED, 1200, new TransmissionControlBlock(channel, 300));
            channel.pipeline().addLast(handler);

            // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
            channel.writeInbound(ConnectionHandshakeSegment.syn(100));

            // peer is dead and therefore no ACK is received
            // wait for user timeout
            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(CLOSED, handler.state);
            });

            assertFalse(channel.isOpen());
        }
    }

    @Nested
    class ConnectionClearing {
        // Both peers are in ESTABLISHED state
        @Test
        void weShouldPerformNormalCloseSequenceOnChannelClose() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, ESTABLISHED, 1200, new TransmissionControlBlock(channel, 99L, 300L));
            channel.pipeline().addLast(handler);

            // trigger close
            final ChannelFuture future = channel.close();
            assertEquals(FIN_WAIT_1, handler.state);
            assertEquals(ConnectionHandshakeSegment.finAck(100, 300), channel.readOutbound());
            assertFalse(future.isDone());

            assertEquals(99, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(300, handler.tcb().rcvNxt);

            // my close got ACKed
            channel.writeInbound(ConnectionHandshakeSegment.ack(300, 101));
            assertEquals(FIN_WAIT_2, handler.state);
            assertFalse(future.isDone());

            assertEquals(101, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(300, handler.tcb().rcvNxt);

            // peer now triggers close as well
            channel.writeInbound(ConnectionHandshakeSegment.finAck(300, 101));
            assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());

            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(CLOSED, handler.state);
            });
            assertTrue(future.isDone());
            assertEquals(101, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(301, handler.tcb().rcvNxt);
        }

        // We're in ESTABLISHED state
        // Peer is dead
        @Test
        void weShouldSucceedCloseSequenceIfPeerIsDead() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ofMillis(100), () -> 100, false, ESTABLISHED, 1200, new TransmissionControlBlock(channel, 100));
            channel.pipeline().addLast(handler);

            // trigger close
            channel.close();

            // wait for user timeout
            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(CLOSED, handler.state);
            });

            assertFalse(channel.isOpen());
        }

        @Test
        void weShouldPerformNormalCloseSequenceWhenPeerInitiateClose() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, ESTABLISHED, 1200, new TransmissionControlBlock(channel, 299));
            channel.pipeline().addLast(handler);

            // peer triggers close
            channel.writeInbound(ConnectionHandshakeSegment.finAck(100, 300));

            // we should trigger a close as well
            assertEquals(LAST_ACK, handler.state);
            assertEquals(ConnectionHandshakeSegment.finAck(300, 101), channel.readOutbound());

            assertEquals(300, handler.tcb().sndUna);
            assertEquals(301, handler.tcb().sndNxt);
            assertEquals(101, handler.tcb().rcvNxt);

            // peer ACKed our close
            channel.writeInbound(ConnectionHandshakeSegment.ack(101, 301));
            assertEquals(CLOSED, handler.state);

            assertEquals(300, handler.tcb().sndUna);
            assertEquals(301, handler.tcb().sndNxt);
            assertEquals(101, handler.tcb().rcvNxt);
        }

        // Both peers are in ESTABLISHED state
        // Both peers initiate close simultaneous
        @Test
        void weShouldPerformSimultaneousCloseIfBothPeersInitiateACloseAtTheSameTime() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, ESTABLISHED, 1200, new TransmissionControlBlock(channel, 100L, 99L, 300L));
            channel.pipeline().addLast(handler);

            // trigger close
            final ChannelFuture future = channel.close();
            assertEquals(FIN_WAIT_1, handler.state);
            assertEquals(ConnectionHandshakeSegment.finAck(100, 300), channel.readOutbound());
            assertFalse(future.isDone());

            assertEquals(100, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(300, handler.tcb().rcvNxt);

            // got parallel close
            channel.writeInbound(ConnectionHandshakeSegment.finAck(300, 100));
            assertEquals(CLOSING, handler.state);
            assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
            assertFalse(future.isDone());

            assertEquals(100, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(301, handler.tcb().rcvNxt);

            channel.writeInbound(ConnectionHandshakeSegment.ack(301, 101));

            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(CLOSED, handler.state);
            });
            assertTrue(future.isDone());
            assertEquals(101, handler.tcb().sndUna);
            assertEquals(101, handler.tcb().sndNxt);
            assertEquals(301, handler.tcb().rcvNxt);
        }
    }

    @Nested
    class UserCallSend {
        @Test
        void shouldSegmentizeOutboundDataIntoSegments() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, 100, new TransmissionControlBlock(channel, 99L, 300L));
            channel.pipeline().addLast(handler);

            // as mss is set to 100, the buf will be segmetized into 100 byte long segments. The last has the PSH flag set.
            final ByteBuf data = Unpooled.buffer(250).writeBytes(randomBytes(250));
            channel.writeOutbound(data);
            assertEquals(ConnectionHandshakeSegment.ack(100, 300, data.slice(0, 100)), channel.readOutbound());
            assertEquals(ConnectionHandshakeSegment.ack(200, 300, data.slice(100, 100)), channel.readOutbound());
            assertEquals(ConnectionHandshakeSegment.pshAck(300, 300, data.slice(200, 50)), channel.readOutbound());

            channel.close();

            data.release();
        }

        @Test
        void passiveServerShouldSwitchToActiveOpenOnWrite() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(ZERO, () -> 100, false, CLOSED, 1200, new TransmissionControlBlock(channel, 100));
            channel.pipeline().addLast(handler);

            // handlerAdded on active channel should change state to LISTEN
            assertEquals(LISTEN, handler.state);

            // write should perform an active OPEN handshake
            final ByteBuf data = Unpooled.buffer(3).writeBytes(randomBytes(3));
            channel.writeOutbound(data);
            assertEquals(ConnectionHandshakeSegment.syn(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            // after handshake the write should be formed
            channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101));
            assertEquals(ESTABLISHED, handler.state);
            assertEquals(ConnectionHandshakeSegment.pshAck(101, 301, data), channel.readOutbound());

            channel.close();
            data.release();
        }

        // FIXME: test bauen wenn peer nicht auf CLOSE antwortet. War früher das hier, oder? https://github.com/drasyl/drasyl/blob/master/drasyl-extras/src/test/java/org/drasyl/handler/connection/ConnectionHandshakeHandlerTest.java#L347
        // FIXME: gleichen test für SYN?
        // FIXME: gleichen test für jeden state? :P

        // FIXME: wann clearen wir unsere write queue?

        @Test
        void shouldFailWriteIfChannelIsClosing() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, FIN_WAIT_1, 1200, new TransmissionControlBlock(channel, 100));
            channel.pipeline().addLast(handler);

            final ByteBuf data = Unpooled.buffer(3).writeBytes(randomBytes(3));
            assertThrows(ConnectionHandshakeException.class, () -> channel.writeOutbound(data));
        }
    }

    @Nested
    class SegmentArrives {
        @Test
        void shouldPassReceivedContentWhenConnectionIsEstablished() {
            final EmbeddedChannel channel = new EmbeddedChannel();
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(Duration.ofMillis(100), () -> 100, false, ESTABLISHED, 1200, new TransmissionControlBlock(channel, 99));
            channel.pipeline().addLast(handler);

            final ByteBuf data = Unpooled.buffer(10).writeBytes(randomBytes(10));
            channel.writeInbound(ConnectionHandshakeSegment.pshAck(300, 100, data));
            assertEquals(data, channel.readInbound());

            channel.close();

            data.release();
        }
    }
}
