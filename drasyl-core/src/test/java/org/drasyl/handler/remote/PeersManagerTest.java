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
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.PeersManager.ClientPeer;
import org.drasyl.handler.remote.PeersManager.Peer;
import org.drasyl.handler.remote.PeersManager.PeerPath;
import org.drasyl.handler.remote.PeersManager.SuperPeer;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(superPeer.getPathCount()).thenReturn(1);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(AddPathAndSuperPeerEvent.class));
        }

        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(superPeer.getPathCount()).thenReturn(2);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(AddPathEvent.class));
        }

        @Test
        void shouldTryToAddPathReturnFalseAndEmitCorrectEventIfNoNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any(), anyShort())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(PathRttEvent.class));
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
        }
    }

    @Nested
    class RemoveSuperPeerPath {
        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfLastPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(peers).remove(peerKey);
            verify(superPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(any(RemoveSuperPeerAndPathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(id);
            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);

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
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(clientPeer.getPathCount()).thenReturn(1);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(AddPathAndChildrenEvent.class));
        }

        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.addPath(any(), any(), anyShort())).thenReturn(true);
            when(clientPeer.getPathCount()).thenReturn(2);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(AddPathEvent.class));
        }

        @Test
        void shouldTryToAddPathReturnFalseAndEmitCorrectEventIfNoNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(clientPeer);
            when(clientPeer.addPath(any(), any(), anyShort())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(id, endpoint, priority);
            verify(ctx).fireUserEventTriggered(any(PathRttEvent.class));
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsClient(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            final short priority = 100;
            final int rtt = 10;
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
        }
    }

    @Nested
    class RemoveClientPath {
        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfLastPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any())).thenReturn(true);
            when(clientPeer.hasPath()).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeClientPath(ctx, peerKey, id));
            verify(peers).remove(peerKey);
            verify(clientPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(any(RemoveChildrenAndPathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any())).thenReturn(true);
            when(clientPeer.hasPath()).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeClientPath(ctx, peerKey, id));
            verify(clientPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.removeClientPath(ctx, peerKey, id));
            verify(clientPeer).removePath(id);
            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsClient(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);

            final PeersManager peersManager = new PeersManager(peers);

            assertThrows(IllegalStateException.class, () -> peersManager.removeClientPath(ctx, peerKey, id));
        }
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnAllPeersWithPathWithGivenId(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey1,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer peer1,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey2,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer peer2) {
            final Class<?> id = Object.class;
            when(peer1.getPaths().containsKey(id)).thenReturn(false);
            when(peer2.getPaths().containsKey(id)).thenReturn(true);

            final PeersManager peersManager = new PeersManager(Map.of(
                    peerKey1, peer1,
                    peerKey2, peer2
            ));

            assertEquals(Set.of(peerKey2), peersManager.getPeers(id));
        }
    }

    @Nested
    class HelloMessageReceived {
        @Test
        void shouldUpdateTimeOnPath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer peer) {
            final Class<?> id = Object.class;
            when(peers.get(peerKey)).thenReturn(peer);

            final PeersManager peersManager = new PeersManager(peers);

            peersManager.helloMessageReceived(peerKey, id);

            verify(peer).helloMessageReceived(id);
        }
    }

    @Nested
    class ApplicationMessageSentOrReceived {
        @Test
        void shouldUpdateTimeOnPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer peer) {
            when(peers.get(peerKey)).thenReturn(peer);

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
            @ValueSource(classes = { SuperPeer.class, ClientPeer.class })
            void shouldReturnTrueIfNewPathHasBeenAdded(final Class<?> peerClazz,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id1 = Object.class;
                final Class<?> id2 = String.class;
                final Class<?> id3 = Number.class;
                final short priority1 = 100;
                final short priority2 = 200;
                final short priority3 = 50;

                final Peer peer = (Peer) peerClazz.getDeclaredConstructor(LongSupplier.class).newInstance(currentTime);

                // first path
                assertTrue(peer.addPath(id1, endpoint, priority1));
                assertEquals(new PeerPath(currentTime, id1, endpoint, priority1, null), peer.firstPath);

                // second path
                assertTrue(peer.addPath(id2, endpoint, priority2));
                assertEquals(new PeerPath(currentTime, id1, endpoint, priority1, new PeerPath(id2, endpoint, priority2)), peer.firstPath);

                // new first path
                assertTrue(peer.addPath(id3, endpoint, priority3));
                assertEquals(new PeerPath(currentTime, id3, endpoint, priority3, new PeerPath(currentTime, id1, endpoint, priority1, new PeerPath(id2, endpoint, priority2))), peer.firstPath);
            }

            @ParameterizedTest
            @ValueSource(classes = { SuperPeer.class, ClientPeer.class })
            void shouldReturnTrueIfNewPathHasBeenAddedAgain(final Class<?> peerClazz,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;

                final Peer peer = (Peer) peerClazz.getDeclaredConstructor(LongSupplier.class).newInstance(currentTime);

                // new path
                assertTrue(peer.addPath(id, endpoint1, priority));
                assertEquals(new PeerPath(currentTime, id, endpoint1, priority, null), peer.firstPath);

                // existing path
                assertFalse(peer.addPath(id, endpoint2, priority));
                assertEquals(new PeerPath(currentTime, id, endpoint2, priority, null), peer.firstPath);
            }

            @ParameterizedTest
            @ValueSource(classes = { SuperPeer.class, ClientPeer.class })
            void shouldThrowExceptionIfEndhostIsUnresolved(final Class<?> peerClazz,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(endpoint.isUnresolved()).thenReturn(true);

                final Peer peer = (Peer) peerClazz.getDeclaredConstructor(LongSupplier.class).newInstance(currentTime);

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(id, endpoint, priority));
            }
        }

        @Nested
        class RemovePath {
            @ParameterizedTest
            @ValueSource(classes = { SuperPeer.class, ClientPeer.class })
            void shouldReturnTrueIfExistingPathHasBeenRemoved(final Class<?> peerClazz,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;

                final PeerPath existingPath = new PeerPath(currentTime, id, endpoint, priority, null);
                final Peer peer = (Peer) peerClazz.getDeclaredConstructor(Map.class, PeerPath.class).newInstance(Map.of(id, existingPath), existingPath);

                assertTrue(peer.removePath(id));
            }

            @ParameterizedTest
            @ValueSource(classes = { SuperPeer.class, ClientPeer.class })
            void shouldReturnFalseIfNonExistingPathWasRequestedToBeRemoved(final Class<?> peerClazz) throws ReflectiveOperationException {
                final Class<?> id = Object.class;

                final Peer peer = (Peer) peerClazz.getDeclaredConstructor(Map.class, PeerPath.class).newInstance(Map.of(), null);

                assertFalse(peer.removePath(id));
            }

            @ParameterizedTest
            @ValueSource(classes = { SuperPeer.class, ClientPeer.class })
            void shouldThrowExceptionIfEndhostIsUnresolved(final Class<?> peerClazz,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) throws ReflectiveOperationException {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(endpoint.isUnresolved()).thenReturn(true);

                final Peer peer = (Peer) peerClazz.getDeclaredConstructor(LongSupplier.class).newInstance(currentTime);

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(id, endpoint, priority));
            }
        }
    }

    @Nested
    class PeerPathTest {
        @Nested
        class HelloMessageReceived {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(currentTime.getAsLong()).thenReturn(123L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, priority, null);

                path.helloMessageReceived();

                assertEquals(123L, path.lastHelloMessageReceivedTime);
            }
        }
    }
}
