/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.remote.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddPathEvent;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.RemovePathEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.remote.handler.LocalNetworkDiscovery.Peer;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofSeconds;
import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalNetworkDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<IdentityPublicKey, Peer> peers;
    @Mock
    private Future<?> pingDisposable;
    private final Duration pingInterval = ofSeconds(1);
    private final Duration pingTimeout = ofSeconds(5);

    @Nested
    class EventHandling {
        @BeforeEach
        void setUp() {
            when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getAddress()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());
        }

        @Test
        void shouldStartHeartbeatingOnChannelActive() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, null));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
            try {
                verify(handler).startHeartbeat(any());
                handler.stopHeartbeat(); // we must stop, otherwise this handler goes crazy cause to the PT0S ping interval
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldStopHeartbeatingAndClearRoutesOnChannelInactive() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
            try {
                pipeline.pipeline().fireChannelInactive();

                verify(handler).stopHeartbeat();
                verify(handler).clearRoutes(any());
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class StartHeartbeat {
        @Test
        void shouldScheduleHeartbeat(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, null));

            handler.startHeartbeat(ctx);

            verify(ctx.executor()).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    class DoHeartbeat {
        @Test
        void shouldRemoveStalePeersAndPingLocalNetworkPeers(@Mock final IdentityPublicKey publicKey,
                                                            @Mock final Peer peer,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(peer.isStale()).thenReturn(true);

            final HashMap<IdentityPublicKey, Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, IdentityTestUtil.ID_1.getAddress(), IdentityTestUtil.ID_1.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.doHeartbeat(ctx);

            verify(peer).isStale();
            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
            assertTrue(peers.isEmpty());
            verify(ctx).writeAndFlush(argThat((ArgumentMatcher<AddressedMessage<?, ?>>) m -> m.message() instanceof DiscoveryMessage && m.address().equals(MULTICAST_ADDRESS)));
        }
    }

    @Nested
    class StopHeartbeat {
        @Test
        void shouldStopHeartbeat() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable));

            handler.stopHeartbeat();

            verify(pingDisposable).cancel(false);
        }
    }

    @Nested
    class ClearRoutes {
        @Test
        void shouldClearRoutes(@Mock final IdentityPublicKey publicKey,
                               @Mock final Peer peer,
                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final HashMap<IdentityPublicKey, Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.clearRoutes(ctx);

            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
            assertTrue(peers.isEmpty());
        }
    }

    @Nested
    class InboundMessageHandling {
        @Test
        void shouldHandleInboundPingFromOtherNodes(@Mock final InetSocketAddress sender,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                   @Mock final Peer peer) {
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
            final DiscoveryMessage msg = DiscoveryMessage.of(0, publicKey, IdentityTestUtil.ID_2.getProofOfWork());
            when(peers.computeIfAbsent(any(), any())).thenReturn(peer);

            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.channelRead(ctx, new AddressedMessage<>(msg, sender));

            verify(ctx).fireUserEventTriggered(any(AddPathEvent.class));
        }

        @Test
        void shouldIgnoreInboundPingFromItself(@Mock final InetSocketAddress sender,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
            when(identity.getIdentityPublicKey()).thenReturn(publicKey);
            when(identity.getAddress()).thenReturn(publicKey);
            final DiscoveryMessage msg = DiscoveryMessage.of(0, publicKey, IdentityTestUtil.ID_2.getProofOfWork());
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.channelRead(ctx, new AddressedMessage<>(msg, sender));

            verify(ctx, never()).fireUserEventTriggered(any(AddPathEvent.class));
        }

        @Test
        void shouldPassthroughUnicastMessages(@Mock final InetSocketAddress sender,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage msg) {
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, sender));

                final ReferenceCounted actual = pipeline.readInbound();
                assertEquals(new AddressedMessage<>(msg, sender), actual);

                actual.release();
            }
            finally {
                pipeline.close();
            }
        }
    }

    @SuppressWarnings({ "SuspiciousMethodCalls" })
    @Test
    void shouldRouteOutboundMessageWhenRouteIsPresent(@Mock final IdentityPublicKey recipient,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
        when(peers.get(any())).thenReturn(peer);

        final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);

        pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

        final ReferenceCounted actual = pipeline.readOutbound();
        assertEquals(new AddressedMessage<>(message, peer.getAddress()), actual);

        actual.release();
    }

    @Test
    void shouldPassthroughMessageWhenRouteIsAbsent(@Mock final IdentityPublicKey recipient,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message) {

        final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getAddress(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

            final ReferenceCounted actual = pipeline.readOutbound();
            assertEquals(new AddressedMessage<>(message, recipient), actual);

            actual.release();
        }
        finally {
            pipeline.close();
        }
    }

    @Nested
    class PeerTest {
        @Nested
        class Getter {
            @Test
            void shouldReturnCorrectValues(@Mock final InetSocketAddress address) {
                final Peer peer = new Peer(pingTimeout, address, 1337L);

                assertSame(address, peer.getAddress());
                assertEquals(1337L, peer.getLastInboundPingTime());
            }
        }

        @Nested
        class InboundPingOccurred {
            @Test
            void shouldUpdateTime(@Mock final InetSocketAddress address) {
                final Peer peer = new Peer(pingTimeout, address, 1337L);

                peer.inboundPingOccurred();

                assertThat(peer.getLastInboundPingTime(), greaterThan(1337L));
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnCorrectValue(@Mock final InetSocketAddress address) {
                assertFalse(new Peer(ofSeconds(60), address, System.currentTimeMillis()).isStale());
                assertTrue(new Peer(ofSeconds(60), address, 1337L).isStale());
            }
        }
    }
}
