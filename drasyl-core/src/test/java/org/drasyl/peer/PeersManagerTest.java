/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;
    private Set<CompressedPublicKey> peers;
    private SetMultimap<CompressedPublicKey, Object> paths;
    private Set<CompressedPublicKey> children;
    @Mock
    private CompressedPublicKey superPeer;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private Identity identity;
    private PeersManager underTest;

    @BeforeEach
    void setUp() {
        peers = new HashSet<>();
        paths = HashMultimap.create();
        children = new HashSet<>();
        underTest = new PeersManager(lock, peers, paths, children, superPeer, eventConsumer, identity);
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnPeers(@Mock final CompressedPublicKey publicKey) {
            peers.add(publicKey);

            assertEquals(Set.of(publicKey), underTest.getPeers());
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
        void shouldReturnChildren(@Mock final CompressedPublicKey publicKey) {
            peers.add(publicKey);
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
    class GetSuperPeerKey {
        @Test
        void shouldReturnSuperKey() {
            assertEquals(superPeer, underTest.getSuperPeerKey());
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
        void shouldReturnPeerInformationAndPaths(@Mock final CompressedPublicKey publicKey,
                                                 @Mock final Object path) {
            peers.add(publicKey);
            paths.put(publicKey, path);

            assertEquals(Set.of(path), underTest.getPaths(publicKey));
        }
    }

    @Nested
    class RemovePath {
        @Test
        void shouldRemovePath(@Mock final CompressedPublicKey publicKey,
                              @Mock final Object path) {
            peers.add(publicKey);
            paths.put(publicKey, path);

            underTest.removePath(publicKey, path);

            assertEquals(Set.of(), paths.get(publicKey));
        }

        @Test
        void shouldEmitNotEventIfPeerHasStillPaths(@Mock final CompressedPublicKey publicKey,
                                                   @Mock final Object path1,
                                                   @Mock final Object path2) {
            peers.add(publicKey);
            paths.put(publicKey, path1);
            paths.put(publicKey, path2);

            underTest.removePath(publicKey, path1);

            verify(eventConsumer, never()).accept(any());
        }

        @Test
        void shouldEmitPeerRelayEventIfNoPathLeftAndThereIsASuperPeer(@Mock final CompressedPublicKey publicKey,
                                                                      @Mock final Object path) {
            peers.add(publicKey);
            paths.put(publicKey, path);

            underTest.removePath(publicKey, path);

            verify(eventConsumer).accept(new PeerRelayEvent(Peer.of(publicKey)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class UnsetSuperPeer {
        @Test
        void shouldUnsetSuperPeer() {
            peers.add(superPeer);

            underTest.unsetSuperPeer();

            assertNull(underTest.getSuperPeerKey());
        }

        @Test
        void shouldEmitNodeOfflineEvent() {
            peers.add(superPeer);

            underTest.unsetSuperPeer();

            verify(eventConsumer).accept(new NodeOfflineEvent(Node.of(identity)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class UnsetSuperPeerAndRemovePath {
        @Test
        void shouldUnsetSuperPeerAndRemovePath(@Mock final Object path) {
            peers.add(superPeer);

            underTest.unsetSuperPeerAndRemovePath(path);

            assertNull(underTest.getSuperPeerKey());
        }

        @Test
        void shouldEmitNodeOfflineEvent(@Mock final Object path) {
            peers.add(superPeer);

            underTest.unsetSuperPeerAndRemovePath(path);

            verify(eventConsumer).accept(new NodeOfflineEvent(Node.of(identity)));
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitNoEventForSuperPeerIfPathsLeft() {
            fail("not implemented");
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class AddPathAndSetSuperPeer {
        @BeforeEach
        void setUp() {
            underTest.unsetSuperPeer();
        }

        @Test
        void shouldAddPathAndSetSuperPeer(@Mock final CompressedPublicKey publicKey,
                                          @Mock final Object path) {
            underTest.addPathAndSetSuperPeer(publicKey, path);

            final CompressedPublicKey superPeerKey = underTest.getSuperPeerKey();
            assertEquals(publicKey, superPeerKey);
        }

        @Test
        void shouldEmitPeerDirectEventForSuperPeerAndNodeOnlineEvent(@Mock final CompressedPublicKey publicKey,
                                                                     @Mock final Object path) {
            underTest.addPathAndSetSuperPeer(publicKey, path);

            verify(eventConsumer).accept(new PeerDirectEvent(Peer.of(publicKey)));
            verify(eventConsumer).accept(new NodeOnlineEvent(Node.of(identity)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock(), times(2)).lock();
            verify(lock.writeLock(), times(2)).unlock();
        }
    }

    @Nested
    class RemoveChildrenAndPath {
        @Test
        void shouldRemoveChildrenAndPath(@Mock final CompressedPublicKey publicKey,
                                         @Mock final Object path) {
            peers.add(publicKey);
            paths.put(publicKey, path);

            underTest.removeChildrenAndPath(publicKey, path);

            assertThat(underTest.getPeers(), not(hasItem(publicKey)));
            assertEquals(Set.of(), underTest.getChildren());
        }

        @Test
        void shouldNotEmitEventWhenRemovingUnknownPeer(@Mock final CompressedPublicKey publicKey,
                                                       @Mock final Object path) {
            underTest.removeChildrenAndPath(publicKey, path);

            verify(eventConsumer, never()).accept(any());
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
        void shouldAddPathAndChildren(@Mock final CompressedPublicKey publicKey,
                                      @Mock final Object path) {
            underTest.addPathAndChildren(publicKey, path);

            assertThat(underTest.getPeers(), hasItem(publicKey));
            assertEquals(Set.of(publicKey), underTest.getChildren());
        }

        @Test
        void shouldEmitPeerDirectEventIfGivenPathIsTheFirstOneForThePeer(@Mock final CompressedPublicKey publicKey,
                                                                         @Mock final Object path) {
            underTest.addPathAndChildren(publicKey, path);

            verify(eventConsumer).accept(new PeerDirectEvent(Peer.of(publicKey)));
        }

        @Test
        void shouldEmitNoEventIfGivenPathIsNotTheFirstOneForThePeer(@Mock final CompressedPublicKey publicKey,
                                                                    @Mock final Object path) {
            peers.add(publicKey);
            paths.put(publicKey, mock(Object.class));

            underTest.addPathAndChildren(publicKey, path);

            verify(eventConsumer, never()).accept(any());
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }
}
