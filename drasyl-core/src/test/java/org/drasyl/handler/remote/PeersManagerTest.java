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
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.PeersManager.AbstractPeer;
import org.drasyl.handler.remote.PeersManager.ClientPeer;
import org.drasyl.handler.remote.PeersManager.PeerPath;
import org.drasyl.handler.remote.PeersManager.SuperPeer;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private Map<DrasylAddress, AbstractPeer> peers;
    public static final Class<?> PATH_ID_1 = Object.class;
    public static final Class<?> PATH_ID_2 = Integer.class;
    public static final Class<?> PATH_ID_3 = String.class;

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
            when(superPeer.addPath(any(), any(), any(), anyShort())).thenReturn(true);
            when(superPeer.getPathCount(any())).thenReturn(1);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(ctx, id, endpoint, priority);
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
            when(superPeer.addPath(any(), any(), any(), anyShort())).thenReturn(true);
            when(superPeer.getPathCount(any())).thenReturn(2);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(ctx, id, endpoint, priority);
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
            when(superPeer.addPath(any(), any(), any(), anyShort())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(superPeer).addPath(ctx, id, endpoint, priority);
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
            when(superPeer.removePath(any(), any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(peers).remove(peerKey);
            verify(superPeer).removePath(ctx, id);
            verify(ctx).fireUserEventTriggered(any(RemoveSuperPeerAndPathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any(), any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(ctx, id);
            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(superPeer);
            when(superPeer.removePath(any(), any())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(ctx, id);
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
            when(clientPeer.addPath(any(), any(), any(), anyShort())).thenReturn(true);
            when(clientPeer.getPathCount(any())).thenReturn(1);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(ctx, id, endpoint, priority);
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
            when(clientPeer.addPath(any(), any(), any(), anyShort())).thenReturn(true);
            when(clientPeer.getPathCount(any())).thenReturn(2);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(ctx, id, endpoint, priority);
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
            when(clientPeer.addPath(any(), any(), any(), anyShort())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.addClientPath(ctx, peerKey, id, endpoint, priority, rtt));
            verify(clientPeer).addPath(ctx, id, endpoint, priority);
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
            when(clientPeer.removePath(any(), any())).thenReturn(true);
            when(clientPeer.hasPath()).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeClientPath(ctx, peerKey, id));
            verify(peers).remove(peerKey);
            verify(clientPeer).removePath(ctx, id);
            verify(ctx).fireUserEventTriggered(any(RemoveChildrenAndPathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any(), any())).thenReturn(true);
            when(clientPeer.hasPath()).thenReturn(true);

            final PeersManager peersManager = new PeersManager(peers);

            assertTrue(peersManager.removeClientPath(ctx, peerKey, id));
            verify(clientPeer).removePath(ctx, id);
            verify(ctx).fireUserEventTriggered(any(RemovePathEvent.class));
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ClientPeer clientPeer) {
            final Class<?> id = Object.class;
            when(peers.get(any())).thenReturn(clientPeer);
            when(clientPeer.removePath(any(), any())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(peers);

            assertFalse(peersManager.removeClientPath(ctx, peerKey, id));
            verify(clientPeer).removePath(ctx, id);
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
    class GetEndpoints {
        @Test
        void shouldOrderEndpointsByPriority(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peer,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint3,
                                            @Mock DrasylServerChannel drasylServerChannel) {
            when(ctx.channel()).thenReturn(drasylServerChannel);

            final PeersManager peersManager = new PeersManager();

            peersManager.addSuperPeerPath(ctx, peer, PATH_ID_1, endpoint1, (short) 5);
            peersManager.addSuperPeerPath(ctx, peer, PATH_ID_2, endpoint2, (short) 7);
            peersManager.addSuperPeerPath(ctx, peer, PATH_ID_3, endpoint3, (short) 6);

            assertEquals(List.of(endpoint1, endpoint3, endpoint2), peersManager.getEndpoints(peer));
        }
    }

    @Nested
    class PeerTest {
        @Nested
        class AddPath {
            @Test
            void shouldReturnTrueIfNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id1 = Object.class;
                final Class<?> id2 = String.class;
                final Class<?> id3 = Number.class;
                final short priority1 = 100;
                final short priority2 = 200;
                final short priority3 = 50;

                final SuperPeer peer = new SuperPeer();

                // first path
                assertTrue(peer.addPath(ctx, id1, endpoint, priority1));
                assertEquals(new PeerPath(currentTime, id1, endpoint, priority1, null), peer.firstPath);

                // second path
                assertTrue(peer.addPath(ctx, id2, endpoint, priority2));
                assertEquals(new PeerPath(currentTime, id1, endpoint, priority1, new PeerPath(id2, endpoint, priority2)), peer.firstPath);

                // new first path
                assertTrue(peer.addPath(ctx, id3, endpoint, priority3));
                assertEquals(new PeerPath(currentTime, id3, endpoint, priority3, new PeerPath(currentTime, id1, endpoint, priority1, new PeerPath(id2, endpoint, priority2))), peer.firstPath);
            }

            @Test
            void shouldReturnTrueIfNewPathHasBeenAddedAgain(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2) {
                final Class<?> id = Object.class;
                final short priority = 100;

                final SuperPeer peer = new SuperPeer();

                // new path
                assertTrue(peer.addPath(ctx, id, endpoint1, priority));
                assertEquals(new PeerPath(currentTime, id, endpoint1, priority, null), peer.firstPath);

                // existing path
                assertFalse(peer.addPath(ctx, id, endpoint2, priority));
                assertEquals(new PeerPath(currentTime, id, endpoint2, priority, null), peer.firstPath);
            }

            @Test
            void shouldThrowExceptionIfEndhostIsUnresolved(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(endpoint.isUnresolved()).thenReturn(true);

                final SuperPeer peer = new SuperPeer();

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(ctx, id, endpoint, priority));
            }
        }

        @Nested
        class RemovePath {
            @Test
            void shouldReturnTrueIfExistingPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final Class<?> id = Object.class;
                final short priority = 100;

                final PeerPath existingPath = new PeerPath(currentTime, id, endpoint, priority, null);
                final SuperPeer peer = new SuperPeer(currentTime, new HashMap<>(Map.of(id, existingPath)), existingPath, 0);

                assertTrue(peer.removePath(ctx, id));
            }

            @Test
            void shouldReturnFalseIfNonExistingPathWasRequestedToBeRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
                final Class<?> id = Object.class;

                final SuperPeer peer = new SuperPeer(currentTime, new HashMap<>(), null, 0);

                assertFalse(peer.removePath(ctx, id));
            }

            @Test
            void shouldThrowExceptionIfEndhostIsUnresolved(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
                final Class<?> id = Object.class;
                final short priority = 100;
                when(endpoint.isUnresolved()).thenReturn(true);

                final SuperPeer peer = new SuperPeer();

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(ctx, id, endpoint, priority));
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
