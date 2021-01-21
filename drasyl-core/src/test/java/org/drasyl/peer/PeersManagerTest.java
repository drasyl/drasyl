/*
 * Copyright (c) 2021.
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
import org.drasyl.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;
    private Map<CompressedPublicKey, PeerInformation> peers;
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
        peers = new HashMap<>();
        paths = HashMultimap.create();
        children = new HashSet<>();
        underTest = new PeersManager(lock, peers, paths, children, superPeer, eventConsumer, identity);
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnPeers(@Mock final CompressedPublicKey publicKey,
                               @Mock final PeerInformation peerInformation) {
            peers.put(publicKey, peerInformation);

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
        void shouldReturnChildren(@Mock final CompressedPublicKey publicKey,
                                  @Mock final PeerInformation peerInformation) {
            peers.put(publicKey, peerInformation);
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
    class GetChildrenKeys {
        @Test
        void shouldReturnChildrenKeys(@Mock final CompressedPublicKey publicKey) {
            children.add(publicKey);

            assertEquals(Set.of(publicKey), underTest.getChildrenKeys());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetPeer {
        @Test
        void shouldReturnPeerInformationAndPaths(@Mock final CompressedPublicKey publicKey,
                                                 @Mock final PeerInformation peerInformation,
                                                 @Mock final Object path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            assertEquals(Pair.of(peerInformation, Set.of(path)), underTest.getPeer(publicKey));
        }
    }

    @Nested
    class SetPeerInformation {
        @Test
        void shouldSetPeerInformation(@Mock final CompressedPublicKey publicKey,
                                      @Mock final PeerInformation peerInformation) {
            underTest.setPeerInformation(publicKey, peerInformation);

            assertNotNull(peers.get(publicKey));
        }

        @Test
        void shouldEmitPeerRelayEventIfThereIsASuperPeer(@Mock final CompressedPublicKey publicKey,
                                                         @Mock final PeerInformation peerInformation) {
            underTest.setPeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerRelayEvent(Peer.of(publicKey)));
        }

        @Test
        void shouldEmitPeerRelayEventIfThereIsNoSuperPeerAndPeerIsChildren(@Mock final CompressedPublicKey publicKey,
                                                                           @Mock final PeerInformation peerInformation) {
            underTest = new PeersManager(lock, peers, paths, children, null, eventConsumer, identity);

            children.add(publicKey);

            underTest.setPeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerRelayEvent(Peer.of(publicKey)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class AddPeer {
        @Test
        void shouldAddEmptyPeerInformation(@Mock final CompressedPublicKey publicKey) {
            underTest.addPeer(publicKey);

            assertEquals(PeerInformation.of(), peers.get(publicKey));
        }
    }

    @Nested
    class RemovePath {
        @Test
        void shouldRemovePath(@Mock final CompressedPublicKey publicKey,
                              @Mock final PeerInformation peerInformation,
                              @Mock final Object path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            underTest.removePath(publicKey, path);

            assertEquals(Set.of(), paths.get(publicKey));
        }

        @Test
        void shouldEmitNotEventIfPeerHasStillPaths(@Mock final CompressedPublicKey publicKey,
                                                   @Mock final PeerInformation peerInformation,
                                                   @Mock final Object path1,
                                                   @Mock final Object path2) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path1);
            paths.put(publicKey, path2);

            underTest.removePath(publicKey, path1);

            verify(eventConsumer, never()).accept(any());
        }

        @Test
        void shouldEmitPeerRelayEventIfNoPathLeftAndThereIsASuperPeer(@Mock final CompressedPublicKey publicKey,
                                                                      @Mock final PeerInformation peerInformation,
                                                                      @Mock final Object path) {
            peers.put(publicKey, peerInformation);
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
        void shouldUnsetSuperPeer(@Mock final PeerInformation peerInformation) {
            peers.put(superPeer, peerInformation);

            underTest.unsetSuperPeer();

            assertNull(underTest.getSuperPeerKey());
        }

        @Test
        void shouldEmitNodeOfflineEvent(@Mock final PeerInformation peerInformation) {
            peers.put(superPeer, peerInformation);

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
        void shouldUnsetSuperPeerAndRemovePath(@Mock final PeerInformation peerInformation,
                                               @Mock final Object path) {
            peers.put(superPeer, peerInformation);

            underTest.unsetSuperPeerAndRemovePath(path);

            assertNull(underTest.getSuperPeerKey());
        }

        @Test
        void shouldEmitNodeOfflineEvent(@Mock final PeerInformation peerInformation,
                                        @Mock final Object path) {
            peers.put(superPeer, peerInformation);

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
    class SetPeerInformationAndAddPathAndSetSuperPeer {
        @BeforeEach
        void setUp() {
            underTest.unsetSuperPeer();
        }

        @Test
        void shouldSetPeerInformationAndAddPathAndSetSuperPeer(@Mock final CompressedPublicKey publicKey,
                                                               @Mock final PeerInformation peerInformation,
                                                               @Mock final Object path) {
            underTest.setPeerInformationAndAddPathAndSetSuperPeer(publicKey, peerInformation, path);

            final CompressedPublicKey superPeerKey = underTest.getSuperPeerKey();
            assertEquals(publicKey, superPeerKey);
        }

        @Test
        void shouldEmitPeerDirectEventForSuperPeerAndNodeOnlineEvent(@Mock final CompressedPublicKey publicKey,
                                                                     @Mock final PeerInformation peerInformation,
                                                                     @Mock final Object path) {
            underTest.setPeerInformationAndAddPathAndSetSuperPeer(publicKey, peerInformation, path);

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
                                         @Mock final PeerInformation peerInformation,
                                         @Mock final Object path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            underTest.removeChildrenAndPath(publicKey, path);

            assertThat(underTest.getPeers(), hasItem(publicKey));
            assertEquals(Set.of(), underTest.getChildrenKeys());
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
    class SetPeerInformationAndAddPathAndChildren {
        @Test
        void shouldSetPeerInformationAndAddPathAndChildren(@Mock final CompressedPublicKey publicKey,
                                                           @Mock final PeerInformation peerInformation,
                                                           @Mock final Object path) {
            underTest.setPeerInformationAndAddPathAndChildren(publicKey, peerInformation, path);

            assertThat(underTest.getPeers(), hasItem(publicKey));
            assertEquals(Set.of(publicKey), underTest.getChildrenKeys());
        }

        @Test
        void shouldEmitPeerDirectEventIfGivenPathIsTheFirstOneForThePeer(@Mock final CompressedPublicKey publicKey,
                                                                         @Mock final PeerInformation peerInformation,
                                                                         @Mock final Object path) {
            underTest.setPeerInformationAndAddPathAndChildren(publicKey, peerInformation, path);

            verify(eventConsumer).accept(new PeerDirectEvent(Peer.of(publicKey)));
        }

        @Test
        void shouldEmitNoEventIfGivenPathIsNotTheFirstOneForThePeer(@Mock final CompressedPublicKey publicKey,
                                                                    @Mock final PeerInformation peerInformation,
                                                                    @Mock final Object path) {
            peers.put(publicKey, mock(PeerInformation.class));
            paths.put(publicKey, mock(Object.class));

            underTest.setPeerInformationAndAddPathAndChildren(publicKey, peerInformation, path);

            verify(eventConsumer, never()).accept(any());
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }
}