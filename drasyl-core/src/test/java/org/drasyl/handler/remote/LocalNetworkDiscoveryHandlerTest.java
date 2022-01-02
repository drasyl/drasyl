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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofSeconds;
import static org.drasyl.handler.remote.UdpMulticastServer.MULTICAST_ADDRESS;
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
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class LocalNetworkDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<DrasylAddress, LocalNetworkDiscovery.Peer> peers;
    @Mock
    private Future<?> pingDisposable;
    private final Duration pingInterval = ofSeconds(1);
    private final Duration pingTimeout = ofSeconds(5);

    @Nested
    class EventHandling {
        @BeforeEach
        void setUp() {
            when(identity.getIdentityPublicKey()).thenReturn(ID_1.getIdentityPublicKey());
            when(identity.getAddress()).thenReturn(ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(ID_1.getProofOfWork());
        }

        @Test
        void shouldStartHeartbeatingOnChannelActive() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, null));
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                verify(handler).startHeartbeat(any());
                handler.stopHeartbeat(); // we must stop, otherwise this handler goes crazy cause to the PT0S ping interval
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldStopHeartbeatingAndClearRoutesOnChannelInactive() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable));
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.pipeline().fireChannelInactive();

                verify(handler).stopHeartbeat();
                verify(handler).clearRoutes(any());
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class StartHeartbeat {
        @Test
        void shouldScheduleHeartbeat(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, null));

            handler.startHeartbeat(ctx);

            verify(ctx.executor()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    class DoHeartbeat {
        @Test
        void shouldRemoveStalePeersAndPingLocalNetworkPeers(@Mock final IdentityPublicKey publicKey,
                                                            @Mock final LocalNetworkDiscovery.Peer peer,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(peer.isStale()).thenReturn(true);

            final HashMap<DrasylAddress, LocalNetworkDiscovery.Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.doHeartbeat(ctx);

            verify(peer).isStale();
            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
            assertTrue(peers.isEmpty());
            verify(ctx).writeAndFlush(argThat((ArgumentMatcher<InetAddressedMessage<?>>) m -> m.content() instanceof HelloMessage && m.recipient().equals(MULTICAST_ADDRESS)));
        }
    }

    @Nested
    class StopHeartbeat {
        @Test
        void shouldStopHeartbeat() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable));

            handler.stopHeartbeat();

            verify(pingDisposable).cancel(false);
        }
    }

    @Nested
    class ClearRoutes {
        @Test
        void shouldClearRoutes(@Mock final IdentityPublicKey publicKey,
                               @Mock final LocalNetworkDiscovery.Peer peer,
                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final HashMap<DrasylAddress, LocalNetworkDiscovery.Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
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
                                                   @Mock final LocalNetworkDiscovery.Peer peer) {
            final IdentityPublicKey publicKey = ID_2.getIdentityPublicKey();
            final HelloMessage msg = HelloMessage.of(0, publicKey, ID_2.getProofOfWork());
            when(peers.computeIfAbsent(any(), any())).thenReturn(peer);

            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.channelRead(ctx, new InetAddressedMessage<>(msg, null, sender));

            verify(ctx).fireUserEventTriggered(any(AddPathEvent.class));
        }

        @Test
        void shouldIgnoreInboundPingFromItself(@Mock final InetSocketAddress sender,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final IdentityPublicKey publicKey = ID_2.getIdentityPublicKey();
            when(ctx.channel().localAddress()).thenReturn(publicKey);
            when(identity.getIdentityPublicKey()).thenReturn(publicKey);
            when(identity.getAddress()).thenReturn(publicKey);
            final HelloMessage msg = HelloMessage.of(0, publicKey, ID_2.getProofOfWork());
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            handler.channelRead(ctx, new InetAddressedMessage<>(msg, null, sender));

            verify(ctx, never()).fireUserEventTriggered(any(AddPathEvent.class));
        }

        @Test
        void shouldPassThroughUnicastMessages(@Mock final InetSocketAddress sender,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage msg) {
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, null, sender));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new InetAddressedMessage<>(msg, null, sender), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }
    }

    @SuppressWarnings({ "SuspiciousMethodCalls" })
    @Test
    void shouldRouteOutboundMessageWhenRouteIsPresent(@Mock final IdentityPublicKey recipient,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final LocalNetworkDiscovery.Peer peer) {
        when(peer.getAddress()).thenReturn(new InetSocketAddress(12345));
        when(peers.get(any())).thenReturn(peer);

        final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);

        channel.writeAndFlush(new OverlayAddressedMessage<>(message, recipient));

        final ReferenceCounted actual = channel.readOutbound();
        assertEquals(new InetAddressedMessage<>(message, peer.getAddress()), actual);

        actual.release();
    }

    @Test
    void shouldPassThroughMessageWhenRouteIsAbsent(@Mock final IdentityPublicKey recipient,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message) {

        final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(peers, identity.getIdentityPublicKey(), identity.getProofOfWork(), pingInterval, pingTimeout, 0, pingDisposable);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
        try {
            channel.writeAndFlush(new OverlayAddressedMessage<>(message, recipient));

            final ReferenceCounted actual = channel.readOutbound();
            assertEquals(new OverlayAddressedMessage<>(message, recipient), actual);

            actual.release();
        }
        finally {
            channel.close();
        }
    }

    @Nested
    class PeerTest {
        @Nested
        class Getter {
            @Test
            void shouldReturnCorrectValues(@Mock final InetSocketAddress address) {
                final LocalNetworkDiscovery.Peer peer = new LocalNetworkDiscovery.Peer(pingTimeout, address, 1337L);

                assertSame(address, peer.getAddress());
                assertEquals(1337L, peer.getLastInboundPingTime());
            }
        }

        @Nested
        class InboundPingOccurred {
            @Test
            void shouldUpdateTime(@Mock final InetSocketAddress address) {
                final LocalNetworkDiscovery.Peer peer = new LocalNetworkDiscovery.Peer(pingTimeout, address, 1337L);

                peer.inboundPingOccurred();

                assertThat(peer.getLastInboundPingTime(), greaterThan(1337L));
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnCorrectValue(@Mock final InetSocketAddress address) {
                assertFalse(new LocalNetworkDiscovery.Peer(ofSeconds(60), address, System.currentTimeMillis()).isStale());
                assertTrue(new LocalNetworkDiscovery.Peer(ofSeconds(60), address, 1337L).isStale());
            }
        }
    }
}
