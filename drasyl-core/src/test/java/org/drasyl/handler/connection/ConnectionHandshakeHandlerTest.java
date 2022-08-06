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
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConnectionHandshakeHandlerTest {
    // "Server" is in CLOSED state
    // "Client" initiate handshake
    @Nested
    class ThreeWayHandshakeConnectionSynchronization {
        @Test
        void asClient() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, true, CLOSED, 0, 0, 0);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // channelActive should trigger SYNchronize of our SEG with peer
            assertEquals(ConnectionHandshakeSegment.syn(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertEquals(100, handler.sndUna);
            assertEquals(101, handler.sndNxt);
            assertEquals(0, handler.rcvNxt);

            // peer SYNchronizes his SEG with us and ACKed our segment, we reply with ACK for his SYN
            channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101));
            assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(101, handler.sndUna);
            assertEquals(101, handler.sndNxt);
            assertEquals(301, handler.rcvNxt);

            assertTrue(channel.isOpen());
            channel.close();
        }

        @Test
        void asServer() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 300, false, CLOSED, 0, 0, 0);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // channelActive should change state to LISTEN
            assertEquals(LISTEN, handler.state);

            // peer wants to SYNchronize his SEG with us, we reply with a SYN/ACK
            channel.writeInbound(ConnectionHandshakeSegment.syn(100));
            assertEquals(ConnectionHandshakeSegment.synAck(300, 101), channel.readOutbound());
            assertEquals(SYN_RECEIVED, handler.state);

            assertEquals(300, handler.sndUna);
            assertEquals(301, handler.sndNxt);
            assertEquals(101, handler.rcvNxt);

            // peer ACKed our SYN
            channel.writeInbound(ConnectionHandshakeSegment.ack(101, 301));
            assertEquals(ESTABLISHED, handler.state);

            assertEquals(301, handler.sndUna);
            assertEquals(301, handler.sndNxt);
            assertEquals(101, handler.rcvNxt);

            assertTrue(channel.isOpen());
            channel.close();
        }
    }

    // Both peers are in CLOSED state
    // Both peers initiate handshake simultaneous
    @Test
    void simultaneousConnectionSynchronization() {
        final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, true, CLOSED, 0, 0, 0);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        // channelActive should trigger SYNchronize of our SEG with peer
        assertEquals(ConnectionHandshakeSegment.syn(100), channel.readOutbound());
        assertEquals(SYN_SENT, handler.state);

        assertEquals(100, handler.sndUna);
        assertEquals(101, handler.sndNxt);
        assertEquals(0, handler.rcvNxt);

        // peer SYNchronizes his SEG before our SYN has been received
        channel.writeInbound(ConnectionHandshakeSegment.syn(300));
        assertEquals(SYN_RECEIVED, handler.state);
        assertEquals(ConnectionHandshakeSegment.synAck(100, 301), channel.readOutbound());

        assertEquals(100, handler.sndUna);
        assertEquals(101, handler.sndNxt);
        assertEquals(301, handler.rcvNxt);

        // peer respond to our SYN with ACK (and another SYN)
        channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101));
        assertEquals(ESTABLISHED, handler.state);

        assertEquals(101, handler.sndUna);
        assertEquals(101, handler.sndNxt);
        assertEquals(301, handler.rcvNxt);

        assertTrue(channel.isOpen());
        channel.close();
    }

    @Test
    void passiveServerSwitchToActiveOpenOnWrite() {
        final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, false, CLOSED, 0, 0, 0);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        // channelActive should change state to LISTEN
        assertEquals(LISTEN, handler.state);

        // write should perform an active OPEN handshake
        final ByteBuf data = Unpooled.wrappedBuffer(new byte[]{
                1,
                2,
                3
        });
        final ChannelFuture writeFuture = channel.writeOneOutbound(data);
        assertEquals(ConnectionHandshakeSegment.syn(100), channel.readOutbound());
        assertFalse(writeFuture.isDone());

        // after handshake the write should be formed
        channel.writeInbound(ConnectionHandshakeSegment.synAck(300, 101));
        assertEquals(ConnectionHandshakeSegment.pshAck(101, 301, data), channel.readOutbound());
        assertTrue(writeFuture.isDone());

        channel.close();
        data.release();
    }

    // One node is in CLOSED state
    // Peer is in ESTABLISHED state
    @Nested
    class HalfOpenConnectionDiscovery {
        @Test
        void weCrashed() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 400, true, CLOSED, 0, 0, 0);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // channelActive should trigger SYNchronize of our SEG with peer
            assertEquals(ConnectionHandshakeSegment.syn(400), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertEquals(400, handler.sndUna);
            assertEquals(401, handler.sndNxt);
            assertEquals(0, handler.rcvNxt);

            // as we got an ACK for an unexpected seq, reset the peer
            channel.writeInbound(ConnectionHandshakeSegment.ack(300, 100));
            assertEquals(ConnectionHandshakeSegment.rst(100), channel.readOutbound());
            assertEquals(SYN_SENT, handler.state);

            assertEquals(400, handler.sndUna);
            assertEquals(401, handler.sndNxt);
            assertEquals(0, handler.rcvNxt);

            assertTrue(channel.isOpen());
            channel.close();
        }

        @Test
        void otherCrashed() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, true, ESTABLISHED, 300, 300, 100);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // other wants to SYNchronize with us, ACK with our expected seq
            channel.writeInbound(ConnectionHandshakeSegment.syn(400));
            assertEquals(ESTABLISHED, handler.state);
            assertEquals(ConnectionHandshakeSegment.ack(300, 100), channel.readOutbound());

            assertEquals(300, handler.sndUna);
            assertEquals(300, handler.sndNxt);
            assertEquals(100, handler.rcvNxt);

            // as we sent an ACK for an unexpected seq, peer will reset us
            final ConnectionHandshakeSegment msg = ConnectionHandshakeSegment.rst(100);
            assertThrows(ConnectionHandshakeException.class, () -> channel.writeInbound(msg));
            assertEquals(CLOSED, handler.state);

            assertEquals(300, handler.sndUna);
            assertEquals(300, handler.sndNxt);
            assertEquals(100, handler.rcvNxt);
        }
    }

    // Both peers are in ESTABLISHED state
    @Nested
    class NormalCloseSequence {
        @Test
        void asClient() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, false, ESTABLISHED, 100, 100, 300);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // trigger close
            final ChannelFuture future = channel.close();
            assertEquals(FIN_WAIT_1, handler.state);
            assertEquals(ConnectionHandshakeSegment.finAck(100, 300), channel.readOutbound());
            assertFalse(future.isDone());

            assertEquals(100, handler.sndUna);
            assertEquals(101, handler.sndNxt);
            assertEquals(300, handler.rcvNxt);

            // my close got ACKed
            channel.writeInbound(ConnectionHandshakeSegment.ack(300, 101));
            assertEquals(FIN_WAIT_2, handler.state);
            assertFalse(future.isDone());

            assertEquals(101, handler.sndUna);
            assertEquals(101, handler.sndNxt);
            assertEquals(300, handler.rcvNxt);

            // peer now triggers close as well
            channel.writeInbound(ConnectionHandshakeSegment.finAck(300, 101));
            assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());

            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertEquals(CLOSED, handler.state);
            });
            assertTrue(future.isDone());
            assertEquals(101, handler.sndUna);
            assertEquals(101, handler.sndNxt);
            assertEquals(301, handler.rcvNxt);
        }

        @Test
        void asServer() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, false, ESTABLISHED, 300, 300, 100);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            // peer triggers close
            channel.writeInbound(ConnectionHandshakeSegment.finAck(100, 300));
            assertEquals(ConnectionHandshakeSegment.ack(300, 101), channel.readOutbound());
//            assertEquals(CLOSE_WAIT, handler.state);

            // we should trigger a close as well
            assertEquals(ConnectionHandshakeSegment.finAck(300, 101), channel.readOutbound());
            assertEquals(LAST_ACK, handler.state);

            assertEquals(300, handler.sndUna);
            assertEquals(301, handler.sndNxt);
            assertEquals(101, handler.rcvNxt);

            // peer ACKed our close
            channel.writeInbound(ConnectionHandshakeSegment.ack(101, 301));
            assertEquals(CLOSED, handler.state);

            assertEquals(300, handler.sndUna);
            assertEquals(301, handler.sndNxt);
            assertEquals(101, handler.rcvNxt);
        }
    }

    // Both peers are in ESTABLISHED state
    // Both peers initiate close simultaneous
    @Test
    void simultaneousClose() {
        final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(0, () -> 100, false, ESTABLISHED, 100, 100, 300);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        // trigger close
        final ChannelFuture future = channel.close();
        assertEquals(FIN_WAIT_1, handler.state);
        assertEquals(ConnectionHandshakeSegment.finAck(100, 300), channel.readOutbound());
        assertFalse(future.isDone());

        assertEquals(100, handler.sndUna);
        assertEquals(101, handler.sndNxt);
        assertEquals(300, handler.rcvNxt);

        // got parallel close
        channel.writeInbound(ConnectionHandshakeSegment.finAck(300, 100));
        assertEquals(CLOSING, handler.state);
        assertEquals(ConnectionHandshakeSegment.ack(101, 301), channel.readOutbound());
        assertFalse(future.isDone());

        assertEquals(100, handler.sndUna);
        assertEquals(101, handler.sndNxt);
        assertEquals(301, handler.rcvNxt);

        channel.writeInbound(ConnectionHandshakeSegment.ack(301, 101));

        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            assertEquals(CLOSED, handler.state);
        });
        assertTrue(future.isDone());
        assertEquals(101, handler.sndUna);
        assertEquals(101, handler.sndNxt);
        assertEquals(301, handler.rcvNxt);
    }

    @Nested
    class UserCallClose {
        @Test
        void shouldFailOnClosedChannel() throws Exception {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(100L, () -> 100, false, ESTABLISHED, 100, 100, 300);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            final ChannelHandlerContext ctx = channel.pipeline().firstContext();
            final ChannelPromise closeFuture = ctx.newPromise();
            handler.close(ctx, closeFuture);

            await().untilAsserted(() -> {
                channel.runScheduledPendingTasks();
                assertTrue(closeFuture.isDone());
                // should fail as the remote peer does not respond to the FIN/ACK
                assertFalse(closeFuture.isSuccess());
            });

            final ChannelPromise writeFuture = ctx.newPromise();
            final ByteBuf data = Unpooled.wrappedBuffer(new byte[]{
                    1,
                    2,
                    3
            });
            handler.write(ctx, data, writeFuture);

            assertTrue(writeFuture.isDone());
            assertFalse(writeFuture.isSuccess());
            assertThat(writeFuture.cause(), instanceOf(ConnectionHandshakeException.class));

            channel.close();
        }
    }

    @Nested
    class SegmentTextHandling {
        @Test
        void shouldPutOutboundDataIntoSegments() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(100L, () -> 100, false, ESTABLISHED, 100, 100, 300);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf data = Unpooled.buffer();
            channel.writeOutbound(data);
            assertEquals(ConnectionHandshakeSegment.pshAck(100, 300, data), channel.readOutbound());

            channel.close();
        }

        @Test
        void shouldExtractSegmentText() {
            final ConnectionHandshakeHandler handler = new ConnectionHandshakeHandler(100L, () -> 100, false, ESTABLISHED, 100, 100, 300);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf data = Unpooled.buffer();
            channel.writeInbound(ConnectionHandshakeSegment.pshAck(300, 100, data));
            assertEquals(data, channel.readInbound());

            channel.close();
        }
    }
}
