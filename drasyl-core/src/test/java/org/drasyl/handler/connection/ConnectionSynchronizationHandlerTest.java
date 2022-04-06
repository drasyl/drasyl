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

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.CLOSED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.ESTABLISHED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.LISTEN;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.SYN_RECEIVED;
import static org.drasyl.handler.connection.ConnectionSynchronizationHandler.State.SYN_SENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConnectionSynchronizationHandlerTest {
    // "Server" is in CLOSED state
    // "Client" initiate handshake
    @Nested
    class ThreeWayHandshakeConnectionSynchronization {
        @Test
        void asClient() {
            final ConnectionSynchronizationHandler handler = new ConnectionSynchronizationHandler(0, () -> 100, false, CLOSED, 0, 0, 0);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // SYNchronize our SEG with peer
            channel.writeOutbound(ConnectionSynchronizationHandler.UserCall.OPEN);
            assertEquals(Segment.syn(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            // peer SYNchronizes his SEG with us and ACKed our segment, we reply with ACK for his SYN
            channel.writeInbound(Segment.synAck(300, 101));
            assertEquals(Segment.ack(101, 301), channel.readOutbound());
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(101, handler.snd_una);
            assertEquals(101, handler.snd_nxt);
            assertEquals(301, handler.rcv_nxt);

            assertTrue(channel.isOpen());
            channel.close();
        }

        @Test
        void asServer() {
            final ConnectionSynchronizationHandler handler = new ConnectionSynchronizationHandler(0, () -> 300, false, LISTEN, 0, 0, 0);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
            channel.writeInbound(Segment.syn(100));
            assertEquals(Segment.synAck(300, 101), channel.readOutbound());
            assertEquals(SYN_RECEIVED, handler.state);

            // peer ACKed our SYN
            channel.writeInbound(Segment.ack(101, 301));
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(301, handler.snd_una);
            assertEquals(301, handler.snd_nxt);
            assertEquals(101, handler.rcv_nxt);

            assertTrue(channel.isOpen());
            channel.close();
        }
    }

    // Both peers are in CLOSED state
    // Both peers initiate handshake simultaneous
    @Test
    void simultaneousConnectionSynchronization() {
        final ConnectionSynchronizationHandler handler = new ConnectionSynchronizationHandler(0, () -> 100, false, CLOSED, 0, 0, 0);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        // SYNchronize our SEG with peer
        channel.writeOutbound(ConnectionSynchronizationHandler.UserCall.OPEN);
        assertEquals(Segment.syn(100), channel.readOutbound());
        assertEquals(SYN_SENT, handler.state);

        assertEquals(100, handler.snd_una);
        assertEquals(101, handler.snd_nxt);
        assertEquals(0, handler.rcv_nxt);

        // peer SYNchronizes his SEG before our SYN has been received
        channel.writeInbound(Segment.syn(300));
        assertEquals(SYN_RECEIVED, handler.state);
        assertEquals(Segment.synAck(100, 301), channel.readOutbound());

        assertEquals(100, handler.snd_una);
        assertEquals(101, handler.snd_nxt);
        assertEquals(301, handler.rcv_nxt);

        // peer respont to our SYN with ACK (and another SYN)
        channel.writeInbound(Segment.synAck(300, 101));
        assertEquals(ESTABLISHED, handler.state);

        assertEquals(101, handler.snd_una);
        assertEquals(101, handler.snd_nxt);
        assertEquals(301, handler.rcv_nxt);

        assertTrue(channel.isOpen());
        channel.close();
    }

    // One node is in CLOSED state
    // Peer is in ESTABLISHED state
    @Nested
    class HalfOpenConnectionDiscovery {
        @Test
        void weCrashed() {
            final ConnectionSynchronizationHandler handler = new ConnectionSynchronizationHandler(0, () -> 400, false, CLOSED, 0, 0, 0);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // we want to SYNchronize with an already synchronized peer
            channel.writeOutbound(ConnectionSynchronizationHandler.UserCall.OPEN);
            assertEquals(Segment.syn(400), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            // as we got an ACK for an unexpected seq, reset the peer
            channel.writeInbound(Segment.ack(300, 100));
            assertEquals(Segment.rst(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertTrue(channel.isOpen());
            channel.close();
        }

        @Test
        void otherCrashed() {
            final ConnectionSynchronizationHandler handler = new ConnectionSynchronizationHandler(0, () -> 100, false, ESTABLISHED, 300, 300, 100);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // other wants to SYNchronize with us, ACK with our expected seq
            channel.writeInbound(Segment.syn(400));
            assertEquals(ESTABLISHED, handler.state);
            assertEquals(Segment.ack(300, 100), channel.readOutbound());

            // as we sent an ACK for an unexpected seq, peer will reset us
            channel.writeInbound(Segment.rst(100));
            assertEquals(CLOSED, handler.state);
            assertFalse(channel.isOpen());
        }
    }
}
