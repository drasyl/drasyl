/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.channel.JavaDrasylServerChannelConfig;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.remote.PeersManager.PathId;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.LongSupplier;

import static org.drasyl.handler.remote.PeersManager.PeerRole.CHILDREN;
import static org.drasyl.handler.remote.PeersManager.PeerRole.SUPER_PEER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private LongSupplier currentTime;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<DrasylAddress, Peer> peers;
    final int rtt = 10;

    @Nested
    class AddSuperPeerPath {
        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfFirstPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any())).thenReturn(true);
            when(superPeer.pathCount()).thenReturn(1);
            when(superPeer.role()).thenReturn(SUPER_PEER);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, rtt));
            verify(superPeer).addPath(id, endpoint);
            verify(ctx).fireUserEventTriggered(superPeer.addPeerEvent(endpoint, id, rtt));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.addPath(any(), any())).thenReturn(true);
            when(superPeer.pathCount()).thenReturn(2);
            when(superPeer.role()).thenReturn(SUPER_PEER);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, rtt));
            verify(superPeer).addPath(id, endpoint);
            verify(ctx).fireUserEventTriggered(superPeer.addPathEvent(endpoint, id, rtt));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToAddPathReturnFalseAndEmitCorrectEventIfNoNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(superPeer.hasPath(any(), any())).thenReturn(true);
            when(superPeer.role()).thenReturn(SUPER_PEER);

            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(peerKey, superPeer), null);

            assertFalse(peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, rtt));
            verify(ctx).fireUserEventTriggered(any(PathRttEvent.class));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(peers.computeIfAbsent(any(), any())).thenReturn(childrenPeer);
            when(childrenPeer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertThrows(IllegalStateException.class, () -> peersManager.addSuperPeerPath(ctx, peerKey, id, endpoint, rtt));
            verify(lock.readLock(), atLeastOnce()).lock();
            verify(lock.readLock(), atLeastOnce()).unlock();
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class RemoveSuperPeerPath {
        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfLastPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(superPeer.removePath(any())).thenReturn(true);
            when(superPeer.hasPath(any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(false);
            when(superPeer.role()).thenReturn(SUPER_PEER);

            peers = new HashMap<>(Map.of(peerKey, superPeer));
            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            assertTrue(peers.isEmpty());
            verify(superPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(superPeer.removePeerEvent(id));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(superPeer.removePath(any())).thenReturn(true);
            when(superPeer.hasPath(any())).thenReturn(true);
            when(superPeer.hasPath()).thenReturn(true);
            when(superPeer.role()).thenReturn(SUPER_PEER);

            final PeersManager peersManager = new PeersManager(currentTime, lock, new HashMap<>(Map.of(peerKey, superPeer)), null);

            assertTrue(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(superPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(superPeer.removePathEvent(id));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(superPeer.role()).thenReturn(SUPER_PEER);
            when(superPeer.hasPath(any())).thenReturn(false);

            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(peerKey, superPeer), null);

            assertFalse(peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(ctx, never()).fireUserEventTriggered(any());
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(childrenPeer.role()).thenReturn(CHILDREN);
            when(childrenPeer.hasPath(any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(peerKey, childrenPeer), null);

            assertThrows(IllegalStateException.class, () -> peersManager.removeSuperPeerPath(ctx, peerKey, id));
            verify(lock.readLock(), atLeastOnce()).lock();
            verify(lock.readLock(), atLeastOnce()).unlock();
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class AddClientPath {
        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfFirstPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(peers.computeIfAbsent(any(), any())).thenReturn(childrenPeer);
            when(childrenPeer.addPath(any(), any())).thenReturn(true);
            when(childrenPeer.pathCount()).thenReturn(1);
            when(childrenPeer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.addChildrenPath(ctx, peerKey, id, endpoint, rtt));
            verify(childrenPeer).addPath(id, endpoint);
            verify(ctx).fireUserEventTriggered(childrenPeer.addPeerEvent(endpoint, id, rtt));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToAddPathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {

            when(peers.computeIfAbsent(any(), any())).thenReturn(childrenPeer);
            when(childrenPeer.addPath(any(), any())).thenReturn(true);
            when(childrenPeer.pathCount()).thenReturn(2);
            when(childrenPeer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.addChildrenPath(ctx, peerKey, id, endpoint, rtt));
            verify(childrenPeer).addPath(id, endpoint);
            verify(ctx).fireUserEventTriggered(childrenPeer.addPathEvent(endpoint, id, rtt));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToAddPathReturnFalseAndEmitCorrectEventIfNoNewPathHasBeenAdded(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(childrenPeer.hasPath(any(), any())).thenReturn(true);
            when(childrenPeer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, new HashMap<>(Map.of(peerKey, childrenPeer)), null);

            assertFalse(peersManager.addChildrenPath(ctx, peerKey, id, endpoint, rtt));
            verify(ctx).fireUserEventTriggered(any(PathRttEvent.class));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsClient(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(peers.computeIfAbsent(any(), any())).thenReturn(superPeer);
            when(superPeer.role()).thenReturn(SUPER_PEER);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertThrows(IllegalStateException.class, () -> peersManager.addChildrenPath(ctx, peerKey, id, endpoint, rtt));
            verify(lock.readLock(), atLeastOnce()).lock();
            verify(lock.readLock(), atLeastOnce()).unlock();
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class RemoveClientPath {
        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfLastPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(childrenPeer.removePath(any())).thenReturn(true);
            when(childrenPeer.hasPath()).thenReturn(false);
            when(childrenPeer.hasPath(any())).thenReturn(true);
            when(childrenPeer.role()).thenReturn(CHILDREN);

            peers = new HashMap<>(Map.of(peerKey, childrenPeer));
            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.removeChildrenPath(ctx, peerKey, id));
            assertTrue(peers.isEmpty());
            verify(childrenPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(childrenPeer.removePeerEvent(id));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToRemovePathAndReturnTrueAndEmitCorrectEventIfAdditionalPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(childrenPeer.removePath(any())).thenReturn(true);
            when(childrenPeer.hasPath()).thenReturn(true);
            when(childrenPeer.hasPath(any())).thenReturn(true);
            when(childrenPeer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, new HashMap<>(Map.of(peerKey, childrenPeer)), null);

            assertTrue(peersManager.removeChildrenPath(ctx, peerKey, id));
            verify(childrenPeer).removePath(id);
            verify(ctx).fireUserEventTriggered(childrenPeer.removePathEvent(id));
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }

        @Test
        void shouldTryToRemovePathAndReturnFalseAndEmitNoEventIfNoPathHasBeenRemoved(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer childrenPeer) {
            when(childrenPeer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(peerKey, childrenPeer), null);

            assertFalse(peersManager.removeChildrenPath(ctx, peerKey, id));
            verify(ctx, never()).fireUserEventTriggered(any());
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }

        @Test
        void shouldThrowExceptionIfPeerWasFormelyNotKnownAsClient(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeer) {
            when(superPeer.role()).thenReturn(SUPER_PEER);
            when(superPeer.hasPath(id)).thenReturn(true);

            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(peerKey, superPeer), null);

            assertThrows(IllegalStateException.class, () -> peersManager.removeChildrenPath(ctx, peerKey, id));
            verify(lock.readLock(), atLeastOnce()).lock();
            verify(lock.readLock(), atLeastOnce()).unlock();
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnAllPeersWithPathWithGivenId(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey1,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer1,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey2,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer2) {
            when(peer1.paths().containsKey(id)).thenReturn(false);
            when(peer2.paths().containsKey(id)).thenReturn(true);
            when(peer1.role()).thenReturn(SUPER_PEER);
            when(peer2.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(
                                                        peerKey1, peer1,
                                                        peerKey2, peer2
                                                ), null);

            assertEquals(Set.of(peerKey2), peersManager.getPeers(id));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }
    }

    @Nested
    class IsStale {
        @Test
        void shouldReturnTrueIfPeerIsStale(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(any())).thenReturn(peer);
            when(peer.isStale(any(), any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.isStale(ctx, peerKey, id));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }
    }

    @Nested
    class IsReachable {
        @Test
        void shouldReturnTrueIfPeerIsReachable(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(any())).thenReturn(peer);
            when(peer.isReachable(any(), any())).thenReturn(true);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.isReachable(ctx, peerKey, id));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }
    }

    @Nested
    class HelloMessageReceived {
        @Test
        void shouldUpdateTimeOnPath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            peersManager.helloMessageReceived(peerKey, id);

            verify(peer).helloMessageReceived(id);
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
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

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.hasApplicationTraffic(ctx, peerKey));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
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

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertEquals(endpoint, peersManager.resolve(peerKey));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
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

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, defaultPeerKey);

            assertEquals(endpoint, peersManager.resolve(peerKey));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }

        @Test
        void shouldReturnDefaultEndpointIfNoPeerPathIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Peer defaultPeer,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
            when(peers.get(peerKey)).thenReturn(null);
            when(peers.get(defaultPeerKey)).thenReturn(defaultPeer);
            when(defaultPeer.resolve()).thenReturn(endpoint);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, defaultPeerKey);

            assertEquals(endpoint, peersManager.resolve(peerKey));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }
    }

    @Nested
    class HasPath {
        @Test
        void shouldReturnTrueIfPeerHasPath(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                           @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.hasPath()).thenReturn(true);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            assertTrue(peersManager.hasPath(peerKey));
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }
    }

    @Nested
    class HasDefaultPeer {
        @Test
        void shouldReturnTrueIfDefaultPeerIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey) {
            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(), defaultPeerKey);

            assertTrue(peersManager.hasDefaultPeer());
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
        }
    }

    @Nested
    class SetDefaultPeer {
        @Test
        void shouldSetDefaultPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress previousDefaultPeerKey,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress defaultPeerKey) {
            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(), previousDefaultPeerKey);

            assertEquals(previousDefaultPeerKey, peersManager.setDefaultPeer(defaultPeerKey));
            assertEquals(defaultPeerKey, peersManager.defaultPeerKey);
            verify(lock.readLock(), atLeastOnce()).lock();
            verify(lock.readLock(), atLeastOnce()).unlock();
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class UnsetDefaultPeer {
        @Test
        void shouldUnsetDefaultPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress previousDefaultPeerKey) {
            final PeersManager peersManager = new PeersManager(currentTime, lock, Map.of(), previousDefaultPeerKey);

            assertEquals(previousDefaultPeerKey, peersManager.unsetDefaultPeer());
            assertNull(peersManager.defaultPeerKey);
            verify(lock.readLock(), atLeastOnce()).lock();
            verify(lock.readLock(), atLeastOnce()).unlock();
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class ApplicationMessageSent {
        @Test
        void shouldUpdateTimeOnPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress peerKey,
                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peers.get(peerKey)).thenReturn(peer);
            when(peer.role()).thenReturn(CHILDREN);

            final PeersManager peersManager = new PeersManager(currentTime, lock, peers, null);

            peersManager.applicationMessageSent(peerKey);

            verify(peer).applicationMessageSent();
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
            verify(lock.writeLock(), never()).lock();
            verify(lock.writeLock(), never()).unlock();
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
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final PathId id1 = new PathId() {
                    @Override
                    public short priority() {
                        return 100;
                    }
                };
                final PathId id2 = new PathId() {
                    @Override
                    public short priority() {
                        return 200;
                    }
                };
                final PathId id3 = new PathId() {
                    @Override
                    public short priority() {
                        return 50;
                    }
                };

                final Peer peer = new Peer(currentTime, address, role);

                // first path
                assertTrue(peer.addPath(id1, endpoint));
                assertEquals(new PeerPath(currentTime, id1, endpoint, null, 0L, 0L, 0L, 0), peer.bestPath);

                // second path
                assertTrue(peer.addPath(id2, endpoint));
                assertEquals(new PeerPath(currentTime, id1, endpoint, new PeerPath(id2, endpoint), 0L, 0L, 0L, 0), peer.bestPath);

                // new first path
                assertTrue(peer.addPath(id3, endpoint));
                assertEquals(new PeerPath(currentTime, id3, endpoint, new PeerPath(currentTime, id1, endpoint, new PeerPath(id2, endpoint), 0L, 0L, 0L, 0), 0L, 0L, 0L, 0), peer.bestPath);
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnTrueIfNewPathHasBeenAddedAgain(final PeerRole role,
                                                            @Mock final DrasylAddress address,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint1,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint2) {
                final Peer peer = new Peer(currentTime, address, role);

                // new path
                assertTrue(peer.addPath(id, endpoint1));
                assertEquals(new PeerPath(currentTime, id, endpoint1, null, 0L, 0L, 0L, 0), peer.bestPath);

                // existing path
                assertFalse(peer.addPath(id, endpoint2));
                assertEquals(new PeerPath(currentTime, id, endpoint2, null, 0L, 0L, 0L, 0), peer.bestPath);
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldThrowExceptionIfEndhostIsUnresolved(final PeerRole role,
                                                           @Mock final DrasylAddress address,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                when(endpoint.isUnresolved()).thenReturn(true);

                final Peer peer = new Peer(currentTime, address, role);

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(id, endpoint));
            }
        }

        @Nested
        class RemovePath {
            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnTrueIfExistingPathHasBeenRemoved(final PeerRole role,
                                                              @Mock final DrasylAddress address,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final PeerPath existingPath = new PeerPath(currentTime, id, endpoint, null, 0L, 0L, 0L, 0);
                final Peer peer = new Peer(address, Map.of(id, existingPath), existingPath, role);

                assertTrue(peer.removePath(id));
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldReturnFalseIfNonExistingPathWasRequestedToBeRemoved(final PeerRole role,
                                                                           @Mock final DrasylAddress address,
                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final PathId id) {
                final Peer peer = new Peer(address, Map.of(), null, role);

                assertFalse(peer.removePath(id));
            }

            @ParameterizedTest
            @EnumSource(PeerRole.class)
            void shouldThrowExceptionIfEndhostIsUnresolved(final PeerRole role,
                                                           @Mock final DrasylAddress address,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                when(endpoint.isUnresolved()).thenReturn(true);

                final Peer peer = new Peer(currentTime, address, role);

                assertThrows(UnresolvedAddressException.class, () -> peer.addPath(id, endpoint));
            }
        }
    }

    @Nested
    class PeerPathTest {
        @Nested
        class HelloMessageSent {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                when(currentTime.getAsLong()).thenReturn(123L).thenReturn(456L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, null, 0L, 0L, 0L, 0);

                path.helloMessageSent();
                path.helloMessageSent();

                assertEquals(123L, path.firstHelloMessageSentTime);
            }
        }

        @Nested
        class HelloMessageReceived {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                when(currentTime.getAsLong()).thenReturn(123L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, null, 0L, 0L, 0L, 0);

                path.helloMessageReceived();

                assertEquals(123L, path.lastHelloMessageReceivedTime);
            }
        }

        @Nested
        class AcknowledgementMessageReceived {
            @Test
            void shouldSetToCurrentTime(@Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final int rtt = 10;
                when(currentTime.getAsLong()).thenReturn(123L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, null, 0L, 0L, 0L, rtt);

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
                                               @Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannelConfig config,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final long lastHelloMessageReceivedTime = 40;
                when(currentTime.getAsLong()).thenReturn(123L);
                when(ctx.channel().config()).thenReturn(config);
                when(config.getHelloTimeout().toMillis()).thenReturn(50L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, null, lastHelloMessageReceivedTime, 0, 0, 0);

                assertTrue(path.isStale(ctx));
            }
        }

        @Nested
        class IsReachable {
            @Test
            void shouldReturnTrueIfPathIsReachable(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final LongSupplier currentTime,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final JavaDrasylServerChannelConfig config,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final PathId id,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress endpoint) {
                final long lastAcknowledgementMessageReceivedTime = 120L;
                when(currentTime.getAsLong()).thenReturn(123L);
                when(ctx.channel().config()).thenReturn(config);
                when(config.getHelloTimeout().toMillis()).thenReturn(50L);

                final PeerPath path = new PeerPath(currentTime, id, endpoint, null, 0, 0, lastAcknowledgementMessageReceivedTime, 0);

                assertTrue(path.isReachable(ctx));
            }
        }
    }
}
