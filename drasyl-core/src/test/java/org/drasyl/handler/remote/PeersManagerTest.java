/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.remote.PeersManager.Peer;
import org.drasyl.handler.remote.PeersManager.PeerPath;
import org.drasyl.handler.remote.PeersManager.PeerRole;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.drasyl.handler.remote.PeersManager.PeerRole.CLIENT;
import static org.drasyl.handler.remote.PeersManager.PeerRole.SUPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<DrasylAddress, Peer> peers;

    @Nested
    class AddSuperPeerPath {
        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfFirstPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(superPeer.pathCount()).thenReturn(1);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(superPeer.addPeerEvent(endpoint, id, rtt));
        }

        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(superPeer.pathCount()).thenReturn(2);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(superPeer.addPathEvent(endpoint, id, rtt));
        }

        @Test
        void shouldTryToAddPathReturnFalseAndEmitCorrectEventIfNoNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any(), anyShort())).thenReturn(false);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(PathRttEvent.class));
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
        }
    }

    @Nested
    class RemoveSuperPeerPath {
        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfLastPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(false);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(peers).remove(peerKey);
            verify(superPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(superPeer.removePeerEvent(id));
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(true);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(superPeer.removePathEvent(id));
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any())).thenReturn(false);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(id);
            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.removeSuperPeerPath(ctx, peerKey, id));
        }
    }

    @Nested
    class AddClientPath {
        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfFirstPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(clientPeer.pathCount()).thenReturn(1);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(clientPeer.addPeerEvent(endpoint, id, rtt));
        }

        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(clientPeer.pathCount()).thenReturn(2);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(clientPeer.addPathEvent(endpoint, id, rtt));
        }

        @Test
        void shouldTryToAddPathReturnFalseAndEmitCorrectEventIfNoNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.addPath(any(), any(), anyShort())).thenReturn(false);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(PathRttEvent.class));
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsClient(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
        }
    }

    @Nested
    class RemoveClientPath {
        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfLastPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any())).thenReturn(true);
            when(clientPeer.hasPath()).thenReturn(false);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeClientPath(ctx, peerKey, id));
            verify(peers).remove(peerKey);
            verify(clientPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(clientPeer.removePeerEvent(id));
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any())).thenReturn(true);
            when(clientPeer.hasPath()).thenReturn(true);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeClientPath(ctx, peerKey, id));
            verify(clientPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(clientPeer.removePathEvent(id));
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any())).thenReturn(false);
            when(clientPeer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.removeClientPath(ctx, peerKey, id));
            verify(clientPeer).removePath(id);
            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsClient(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.role()).thenReturn(SUPER);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.removeClientPath(ctx, peerKey, id));
        }
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnAllPeersWithPathWithGivenId(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey1,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer1,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey2,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer2) {
            final Class<?> id = Object.class;
            when(peer1.paths().containsKey(id)).thenReturn(false);
            when(peer2.paths().containsKey(id)).thenReturn(true);
            when(peer1.role()).thenReturn(SUPER);
            when(peer2.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(Map.of(
                    peerKey1, peer1,
                    peerKey2, peer2
            ));

            assertEquals(Set.of(peerKey2), peersManager.getPeers(id));
        }
    }

    @Nested
    class IsStale {
        @Test
        void shouldReturnTrueIfPeerIsStale(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(peer);
            when(peer.isStale(any(), any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.isStale(ctx, peerKey, id));
        }
    }

    @Nested
    class IsReachable {
        @Test
        void shouldReturnTrueIfPeerIsReachable(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(peer);
            when(peer.isReachable(any(), any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.isReachable(ctx, peerKey, id));
        }
    }

    @Nested
    class IsNew {
        @Test
        void shouldReturnTrueIfPeerIsNew(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                         @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                         @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(peer);
            when(peer.isNew(any(), any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.isNew(ctx, peerKey, id));
        }
    }

    @Nested
    class HelloMessageReceived {
        @Test
        void shouldUpdateTimeOnPath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            final Class<?> id = Object.class;
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            peersManager.helloMessageReceived(peerKey, id);

            verify(peer).helloMessageReceived(id);
        }
    }

    @Nested
    class HasApplicationTraffic {
        @Test
        void shouldReturnTrueIfPeerHasApplicationTraffic(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(any())).thenReturn(peer);
            when(peer.hasApplicationTraffic(any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.hasApplicationTraffic(ctx, peerKey));
        }
    }

    @Nested
    class Resolve {
        @Test
        void shouldReturnPeerPathIfPresent(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
            when(peers.get(any())).thenReturn(peer);
            when(peer.resolve()).thenReturn(endpoint);

            final PeersManager peersManager = new PeersManager(peers);

            assertEquals(endpoint, peersManager.resolve(peerKey));
        }

        @Test
        void shouldReturnDefaultEndpointIfNoPeerIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final Peer defaultPeer,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.resolve()).thenReturn(null);
            when(peers.get(defaultPeerKey)).thenReturn(defaultPeer);
            when(defaultPeer.resolve()).thenReturn(endpoint);

            final PeersManager peersManager = new PeersManager(peers, defaultPeerKey);

            assertEquals(endpoint, peersManager.resolve(peerKey));
        }

        @Test
        void shouldReturnDefaultEndpointIfNoPeerPathIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Peer defaultPeer,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
            when(peers.get(peerKey)).thenReturn(null);
            when(peers.get(defaultPeerKey)).thenReturn(defaultPeer);
            when(defaultPeer.resolve()).thenReturn(endpoint);

            final PeersManager peersManager = new PeersManager(peers, defaultPeerKey);

            assertEquals(endpoint, peersManager.resolve(peerKey));
        }
    }

    @Nested
    class HasPath {
        @Test
        void shouldReturnTrueIfPeerHasPath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.hasPath()).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.hasPath(peerKey));
        }
    }

    @Nested
    class HasDefaultPeer {
        @Test
        void shouldReturnTrueIfDefaultPeerIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey) {
            final PeersManager peersManager = new PeersManager(Map.of(), defaultPeerKey);

            assertTrue(peersManager.hasDefaultPeer());
        }
    }

    @Nested
    class SetDefaultPeer {
        @Test
        void shouldSetDefaultPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress previousDefaultPeerKey,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey) {
            final PeersManager peersManager = new PeersManager(Map.of(), previousDefaultPeerKey);

            assertEquals(previousDefaultPeerKey, peersManager.setDefaultPeer(defaultPeerKey));
            assertEquals(defaultPeerKey, peersManager.defaultPeerKey);
        }
    }

    @Nested
    class UnsetDefaultPeer {
        @Test
        void shouldUnsetDefaultPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress previousDefaultPeerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey) {
            final PeersManager peersManager = new PeersManager(Map.of(), previousDefaultPeerKey);

            assertEquals(previousDefaultPeerKey, peersManager.unsetDefaultPeer());
            assertNull(peersManager.defaultPeerKey);
        }
    }

    @Nested
    class ApplicationMessageSentOrReceived {
        @Test
        void shouldUpdateTimeOnPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.role()).thenReturn(CLIENT);

            final PeersManager peersManager = new PeersManager(peers);

            peersManager.applicationMessageSentOrReceived(peerKey);

            verify(peer).applicationMessageSentOrReceived();
        }
    }

    @Nested
    class PeerTest {
        @Mock(answer = RETURNS_DEEP_STUBS)
        private LongSupplier currentTime;

        @Nested
        class AddPath {
            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnTrueIfNewPathHasBeenAdded(final PeerRole role,
                                                       @Mock final DrasylAddress address,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id1 = Object.class;
                final Class<?> id2 = String.class;
                final Class<?> id3 = Number.class;
                final short priority1 = 100;
                final short priority2 = 200;
                final short priority3 = 50;

                final Peer peer = new Peer(currentTime, address, role);

                // first path
                assertTrue(peer.addPath(id1, endpoint, priority1));
                assertEquals(new PeerPath(currentTime, id1, endpoint, priority1, null, 0L, 0L, 0L, 0), peer.firstPath);

                // second path
                assertTrue(peer.addPath(id2, endpoint, priority2));
                assertEquals(new PeerPath(currentTime, id1, endpoint, priority1, new PeerPath(id2, endpoint, priority2), 0L, 0L, 0L, 0), peer.firstPath);

                // new first path
                assertTrue(peer.addPath(id3, endpoint, priority3));
                assertEquals(new PeerPath(currentTime, id3, endpoint, priority3, new PeerPath(currentTime, id1, endpoint, priority1, new PeerPath(id2, endpoint, priority2), 0L, 0L, 0L, 0), 0L, 0L, 0L, 0), peer.firstPath);
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnTrueIfNewPathHasBeenAddedAgain(final PeerRole role,
                                                            @Mock final DrasylAddress address,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;

                final Peer peer = new Peer(currentTime, address, role);

                // new path
                assertTrue(peer.addPath(id, endpoint1, priority));
                assertEquals(new PeerPath(currentTime, id, endpoint1, priority, null, 0L, 0L, 0L, 0), peer.firstPath);

                // existing path
                assertFalse(peer.addPath(id, endpoint2, priority));
                assertEquals(new PeerPath(currentTime, id, endpoint2, priority, null, 0L, 0L, 0L, 0), peer.firstPath);
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldThrowExceptionIfEndhostIsUnresolved(final PeerRole role,
                                                           @Mock final DrasylAddress address,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(endpoint.isUnresolved()).thenReturn(true);

                final Peer peer = new Peer(currentTime, address, role);

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(id, endpoint, priority));
            }
        }

        @Nested
        class RemovePath {
            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnTrueIfExistingPathHasBeenRemoved(final PeerRole role,
                                                              @Mock final DrasylAddress address,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;

                final PeerPath existingPath = new PeerPath(currentTime, id, endpoint, priority, null, 0L, 0L, 0L, 0);
                final Peer peer = new Peer(address, Map.of(id, existingPath), existingPath, role);

                assertTrue(peer.removePath(id));
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnFalseIfNonExistingPathWasRequestedToBeRemoved(final PeerRole role,
                                                                           @Mock final DrasylAddress address) throws ReflectiveOperationException {
                final Class<?> id = Object.class;

                final Peer peer = new Peer(address, Map.of(), null, role);

                assertFalse(peer.removePath(id));
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldThrowExceptionIfEndhostIsUnresolved(final PeerRole role,
                                                           @Mock final DrasylAddress address,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(endpoint.isUnresolved()).thenReturn(true);

                final Peer peer = new Peer(currentTime, address, role);

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(id, endpoint, priority));
            }
        }
    }

    @Nested
    class PeerPathTest {
        @Nested
        class HelloMessageSent {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(currentTime.getAsLong()).thenReturn(123L).thenReturn(456L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null, 0L, 0L, 0L, 0);

                path.helloMessageSent();
                path.helloMessageSent();

                assertEquals(123L, path.firstHelloMessageSentTime);
            }
        }

        @Nested
        class HelloMessageReceived {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(currentTime.getAsLong()).thenReturn(123L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null, 0L, 0L, 0L, 0);

                path.helloMessageReceived();

                assertEquals(123L, path.lastHelloMessageReceivedTime);
            }
        }

        @Nested
        class AcknowledgementMessageReceived {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                final int rtt = 10;
                when(currentTime.getAsLong()).thenReturn(123L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null, 0L, 0L, 0L, rtt);

                path.acknowledgementMessageReceived(rtt);

                assertEquals(123L, path.lastAcknowledgementMessageReceivedTime);
                assertEquals(10, path.rtt);
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnTrueIfPathIsStale(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannelConfig config,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                final long lastHelloMessageReceivedTime = 40;
                when(currentTime.getAsLong()).thenReturn(123L);
                when(ctx.channel().config()).thenReturn(config);
                when(config.getHelloTimeout().toMillis()).thenReturn(50L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null, lastHelloMessageReceivedTime, 0, 0, 0);

                assertTrue(path.isStale(ctx));
            }
        }

        @Nested
        class IsReachable {
            @Test
            void shouldReturnTrueIfPathIsReachable(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannelConfig config,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                final long lastAcknowledgementMessageReceivedTime = 120L;
                when(currentTime.getAsLong()).thenReturn(123L);
                when(ctx.channel().config()).thenReturn(config);
                when(config.getHelloTimeout().toMillis()).thenReturn(50L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null, 0, 0, lastAcknowledgementMessageReceivedTime, 0);

                assertTrue(path.isReachable(ctx));
            }
        }

        @Nested
        class IsNew {
            @Test
            void shouldReturnTrueIfPathIsNew(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannelConfig config,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                final long firstHelloMessageSentTime = 110L;
                final long lastAcknowledgementMessageReceivedTime = 40;
                when(currentTime.getAsLong()).thenReturn(123L);
                when(ctx.channel().config()).thenReturn(config);
                when(config.getHelloTimeout().toMillis()).thenReturn(50L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null, 0, firstHelloMessageSentTime, lastAcknowledgementMessageReceivedTime, 0);

                assertTrue(path.isNew(ctx));
            }
        }
    }
}
