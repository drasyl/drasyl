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
package org.drasyl.peer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;
    private SetMultimap<IdentityPublicKey, Object> paths;
    private Set<IdentityPublicKey> children;
    private Set<IdentityPublicKey> superPeers;
    private PeersManager underTest;

    @BeforeEach
    void setUp() {
        paths = HashMultimap.create();
        children = new HashSet<>();
        superPeers = new HashSet<>();
        underTest = new PeersManager(lock, paths, children, superPeers);
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnAllPeers(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                  @Mock final IdentityPublicKey superPeer,
                                  @Mock final IdentityPublicKey children,
                                  @Mock final IdentityPublicKey peer,
                                  @Mock final Object path) {
            when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
            underTest.addPathAndSuperPeer(ctx, superPeer, path);
            underTest.addPathAndChildren(ctx, children, path);
            underTest.addPath(ctx, peer, path);

            assertEquals(Set.of(superPeer, children, peer), underTest.getPeers());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetChildren {
        @Test
        void shouldReturnChildren(@Mock final IdentityPublicKey publicKey) {
            children.add(publicKey);

            assertEquals(Set.of(publicKey), underTest.getChildren());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetSuperPeers {
        @Test
        void shouldReturnSuperPeers(@Mock final IdentityPublicKey publicKey) {
            superPeers.add(publicKey);

            assertEquals(Set.of(publicKey), underTest.getSuperPeers());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetPaths {
        @Test
        void shouldReturnPaths(@Mock final MigrationHandlerContext ctx,
                               @Mock final IdentityPublicKey publicKey,
                               @Mock final Object path) {
            paths.put(publicKey, path);

            assertEquals(Set.of(path), underTest.getPaths(publicKey));
        }
    }

    @Nested
    class AddPath {
        @Test
        void shouldEmitEventIfThisIsTheFirstPath(@Mock final MigrationHandlerContext ctx,
                                                 @Mock final IdentityPublicKey publicKey,
                                                 @Mock final Object path) {
            underTest.addPath(ctx, publicKey, path);

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> PeerDirectEvent.of(Peer.of(publicKey)).equals(e.event())));
        }

        @Test
        void shouldEmitNotEventIfPeerHasAlreadyPaths(@Mock final MigrationHandlerContext ctx,
                                                     @Mock final IdentityPublicKey publicKey,
                                                     @Mock final Object path1,
                                                     @Mock final Object path2) {
            paths.put(publicKey, path1);

            underTest.addPath(ctx, publicKey, path2);

            verify(ctx, never()).fireUserEventTriggered(any());
        }
    }

    @Nested
    class RemovePath {
        @Test
        void shouldRemovePath(@Mock final MigrationHandlerContext ctx,
                              @Mock final IdentityPublicKey publicKey,
                              @Mock final Object path) {
            paths.put(publicKey, path);

            underTest.removePath(ctx, publicKey, path);

            assertThat(paths.get(publicKey), not(contains(path)));
        }

        @Test
        void shouldEmitNotEventIfPeerHasStillPaths(@Mock final MigrationHandlerContext ctx,
                                                   @Mock final IdentityPublicKey publicKey,
                                                   @Mock final Object path1,
                                                   @Mock final Object path2) {
            paths.put(publicKey, path1);
            paths.put(publicKey, path2);

            underTest.removePath(ctx, publicKey, path1);

            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @Test
        void shouldEmitPeerRelayEventIfNoPathLeftAndThereIsASuperPeer(@Mock final MigrationHandlerContext ctx,
                                                                      @Mock final IdentityPublicKey publicKey,
                                                                      @Mock final Object path) {
            paths.put(publicKey, path);

            underTest.removePath(ctx, publicKey, path);

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> PeerRelayEvent.of(Peer.of(publicKey)).equals(e.event())));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class RemoveSuperPeerAndPath {
        @Test
        void shouldRemoveSuperPeerAndPath(@Mock final MigrationHandlerContext ctx,
                                          @Mock final IdentityPublicKey publicKey,
                                          @Mock final Object path) {
            underTest.removeSuperPeerAndPath(ctx, publicKey, path);

            assertEquals(Set.of(), underTest.getSuperPeers());
        }

        @Test
        void shouldEmitNodeOfflineEventWhenRemovingLastSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                                                 @Mock final IdentityPublicKey publicKey,
                                                                 @Mock final Object path) {
            when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
            superPeers.add(publicKey);

            underTest.removeSuperPeerAndPath(ctx, publicKey, path);

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> e.event() instanceof NodeOfflineEvent));
        }

        @Test
        void shouldNotEmitNodeOfflineEventWhenRemovingNonLastSuperPeer(@Mock final MigrationHandlerContext ctx,
                                                                       @Mock final IdentityPublicKey publicKey,
                                                                       @Mock final IdentityPublicKey publicKey2,
                                                                       @Mock final Object path) {
            superPeers.add(publicKey2);
            superPeers.add(publicKey);

            underTest.removeSuperPeerAndPath(ctx, publicKey, path);

            verify(ctx, never()).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> e.event() instanceof NodeOfflineEvent));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class AddPathAndSuperPeer {
        @Test
        void shouldAddPathAndAddSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                          @Mock final IdentityPublicKey publicKey,
                                          @Mock final Object path) {
            when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
            underTest.addPathAndSuperPeer(ctx, publicKey, path);

            assertEquals(Set.of(publicKey), underTest.getSuperPeers());
            assertEquals(Set.of(path), underTest.getPaths(publicKey));
        }

        @Test
        void shouldEmitPeerDirectEventForSuperPeerAndNodeOnlineEvent(@Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                                                     @Mock final IdentityPublicKey publicKey,
                                                                     @Mock final Object path) {
            when(ctx.attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
            underTest.addPathAndSuperPeer(ctx, publicKey, path);

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> PeerDirectEvent.of(Peer.of(publicKey)).equals(e.event())));
            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> e.event() instanceof NodeOnlineEvent));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class RemoveChildrenAndPath {
        @Test
        void shouldRemoveChildrenAndPath(@Mock final MigrationHandlerContext ctx,
                                         @Mock final IdentityPublicKey publicKey,
                                         @Mock final Object path) {
            paths.put(publicKey, path);

            underTest.removeChildrenAndPath(ctx, publicKey, path);

            assertThat(underTest.getPeers(), not(hasItem(publicKey)));
            assertEquals(Set.of(), underTest.getChildren());
        }

        @Test
        void shouldNotEmitEventWhenRemovingUnknownPeer(@Mock final MigrationHandlerContext ctx,
                                                       @Mock final IdentityPublicKey publicKey,
                                                       @Mock final Object path) {
            underTest.removeChildrenAndPath(ctx, publicKey, path);

            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class AddPathAndChildren {
        @Test
        void shouldAddPathAndChildren(@Mock final MigrationHandlerContext ctx,
                                      @Mock final IdentityPublicKey publicKey,
                                      @Mock final Object path) {
            underTest.addPathAndChildren(ctx, publicKey, path);

            assertThat(underTest.getPeers(), hasItem(publicKey));
            assertEquals(Set.of(publicKey), underTest.getChildren());
        }

        @Test
        void shouldEmitPeerDirectEventIfGivenPathIsTheFirstOneForThePeer(@Mock final MigrationHandlerContext ctx,
                                                                         @Mock final IdentityPublicKey publicKey,
                                                                         @Mock final Object path) {
            underTest.addPathAndChildren(ctx, publicKey, path);

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<MigrationEvent>) e -> PeerDirectEvent.of(Peer.of(publicKey)).equals(e.event())));
        }

        @Test
        void shouldEmitNoEventIfGivenPathIsNotTheFirstOneForThePeer(@Mock final MigrationHandlerContext ctx,
                                                                    @Mock final IdentityPublicKey publicKey,
                                                                    @Mock final Object path,
                                                                    @Mock final Object o) {
            paths.put(publicKey, o);

            underTest.addPathAndChildren(ctx, publicKey, path);

            verify(ctx, never()).fireUserEventTriggered(any());
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }
}
