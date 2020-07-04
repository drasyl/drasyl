/*
 * Copyright (c) 2020.
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
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.event.PeerUnreachableEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.Pair;
import org.drasyl.util.Triple;
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
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;
    private Map<CompressedPublicKey, PeerInformation> peers;
    private SetMultimap<CompressedPublicKey, Path> paths;
    private Set<CompressedPublicKey> children;
    private Map<CompressedPublicKey, CompressedPublicKey> grandchildrenRoutes;
    @Mock
    private CompressedPublicKey superPeer;
    @Mock
    private Consumer<Event> eventConsumer;
    private PeersManager underTest;

    @BeforeEach
    void setUp() {
        peers = new HashMap<>();
        paths = HashMultimap.create();
        children = new HashSet<>();
        grandchildrenRoutes = new HashMap<>();
        underTest = new PeersManager(lock, peers, paths, children, grandchildrenRoutes, superPeer, eventConsumer);
    }

    @Nested
    class GetPeers {
        @Test
        void shouldReturnPeers(@Mock CompressedPublicKey publicKey,
                               @Mock PeerInformation peerInformation) {
            peers.put(publicKey, peerInformation);

            assertEquals(Map.of(publicKey, peerInformation), underTest.getPeers());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetChildrenAndGrandchildren {
        @Test
        void shouldReturnChildrenAndGrandchildren(@Mock CompressedPublicKey publicKey,
                                                  @Mock PeerInformation peerInformation) {
            peers.put(publicKey, peerInformation);
            children.add(publicKey);

            assertEquals(Map.of(publicKey, peerInformation), underTest.getChildrenAndGrandchildren());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetGrandchildrenRoutes {
        @Test
        void shouldReturnGrandchildrenRoutes(@Mock CompressedPublicKey grandchildren,
                                             @Mock CompressedPublicKey children) {
            grandchildrenRoutes.put(grandchildren, children);

            assertEquals(Map.of(grandchildren, children), underTest.getGrandchildrenRoutes());
        }

        @AfterEach
        void tearDown() {
            verify(lock.readLock()).lock();
            verify(lock.readLock()).unlock();
        }
    }

    @Nested
    class GetSuperPeer {
        @Test
        void shouldReturnSuperPeer(@Mock PeerInformation peerInformation,
                                   @Mock Path path) {
            peers.put(superPeer, peerInformation);
            paths.put(superPeer, path);

            assertEquals(Triple.of(superPeer, peerInformation, Set.of(path)), underTest.getSuperPeer());
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
        void shouldReturnChildrenKeys(@Mock CompressedPublicKey publicKey) {
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
        void shouldReturnPeerInformationAndPaths(@Mock CompressedPublicKey publicKey,
                                                 @Mock PeerInformation peerInformation,
                                                 @Mock Path path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            assertEquals(Pair.of(peerInformation, Set.of(path)), underTest.getPeer(publicKey));
        }
    }

    @Nested
    class SetPeerInformation {
        @Test
        void shouldSetPeerInformation(@Mock CompressedPublicKey publicKey,
                                      @Mock PeerInformation peerInformation) {
            underTest.setPeerInformation(publicKey, peerInformation);

            assertNotNull(peers.get(publicKey));
        }

        @Test
        void shouldEmitPeerRelayEventIfThereIsASuperPeer(@Mock CompressedPublicKey publicKey,
                                                         @Mock PeerInformation peerInformation) {
            underTest.setPeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerRelayEvent(new Peer(publicKey)));
        }

        @Test
        void shouldEmitPeerUnreachableEventIfThereIsNoSuperPeer(@Mock CompressedPublicKey publicKey,
                                                                @Mock PeerInformation peerInformation) {
            underTest = new PeersManager(lock, peers, paths, children, grandchildrenRoutes, null, eventConsumer);

            underTest.setPeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerUnreachableEvent(new Peer(publicKey)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class RemovePath {
        @Test
        void shouldRemovePath(@Mock CompressedPublicKey publicKey,
                              @Mock PeerInformation peerInformation,
                              @Mock Path path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            underTest.removePath(publicKey, path);

            assertEquals(Set.of(), paths.get(publicKey));
        }

        @Test
        void shouldEmitNotEventIfPeerHasStillPaths(@Mock CompressedPublicKey publicKey,
                                                   @Mock PeerInformation peerInformation,
                                                   @Mock Path path1,
                                                   @Mock Path path2) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path1);
            paths.put(publicKey, path2);

            underTest.removePath(publicKey, path1);

            verify(eventConsumer, never()).accept(any());
        }

        @Test
        void shouldEmitPeerRelayEventIfNoPathLeftAndThereIsASuperPeer(@Mock CompressedPublicKey publicKey,
                                                                      @Mock PeerInformation peerInformation,
                                                                      @Mock Path path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            underTest.removePath(publicKey, path);

            verify(eventConsumer).accept(new PeerRelayEvent(new Peer(publicKey)));
        }

        @Test
        void shouldEmitPeerUnreachableEventIfThereIsNoSuperPeer(@Mock CompressedPublicKey publicKey,
                                                                @Mock PeerInformation peerInformation,
                                                                @Mock Path path) {
            underTest = new PeersManager(lock, peers, paths, children, grandchildrenRoutes, null, eventConsumer);
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            underTest.removePath(publicKey, path);

            verify(eventConsumer).accept(new PeerUnreachableEvent(new Peer(publicKey)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class AddGrandchildrenRoute {
        @Test
        void shouldAddGrandchildrenRoute(@Mock CompressedPublicKey grandchildren,
                                         @Mock CompressedPublicKey children) {
            underTest.addGrandchildrenRoute(grandchildren, children);

            assertEquals(children, grandchildrenRoutes.get(grandchildren));
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitPeerRelayEventForGrandchildren(@Mock CompressedPublicKey grandchildren,
                                                      @Mock CompressedPublicKey children) {
            underTest.addGrandchildrenRoute(grandchildren, children);

            verify(eventConsumer).accept(new PeerRelayEvent(new Peer(grandchildren)));
        }

        @AfterEach
        void tearDown() {
            verify(lock.writeLock()).lock();
            verify(lock.writeLock()).unlock();
        }
    }

    @Nested
    class RemoveGrandchildrenRoute {
        @Test
        void shouldRemoveGrandchildrenRoute(@Mock CompressedPublicKey grandchildren,
                                            @Mock CompressedPublicKey children) {
            grandchildrenRoutes.put(grandchildren, children);

            underTest.removeGrandchildrenRoute(grandchildren);

            assertNull(grandchildrenRoutes.get(grandchildren));
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitPeerUnknownEventForGrandchildren() {
            fail("not implemented (introduce PeerUnknownEvent?)");
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
        void shouldUnsetSuperPeer(@Mock PeerInformation peerInformation) {
            peers.put(superPeer, peerInformation);

            underTest.unsetSuperPeer();

            assertNull(underTest.getSuperPeer());
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitPeerUnreachableEventForSuperPeer(@Mock PeerInformation peerInformation) {
            peers.put(superPeer, peerInformation);

            underTest.unsetSuperPeer();

            verify(eventConsumer).accept(new PeerDirectEvent(new Peer(superPeer)));
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitPeerUnreachableForAllPeersWithoutDirectConnection() {
            fail("not implemented");
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
        void shouldUnsetSuperPeerAndRemovePath(@Mock PeerInformation peerInformation,
                                               @Mock Path path) {
            peers.put(superPeer, peerInformation);

            underTest.unsetSuperPeerAndRemovePath(path);

            assertNull(underTest.getSuperPeer());
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitPeerUnreachableEventForSuperPeerIfNoPathsLeft() {
            fail("not implemented");
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
        @Test
        void shouldSetPeerInformationAndAddPathAndSetSuperPeer(@Mock CompressedPublicKey publicKey,
                                                               @Mock PeerInformation peerInformation,
                                                               @Mock Path path) {
            underTest.setPeerInformationAndAddPathAndSetSuperPeer(publicKey, peerInformation, path);

            Triple<CompressedPublicKey, PeerInformation, Set<Path>> superPeer = underTest.getSuperPeer();
            assertEquals(publicKey, superPeer.first());
            assertEquals(Set.of(path), superPeer.third());
        }

        @Test
        void shouldEmitPeerDirectEventForSuperPeer(@Mock CompressedPublicKey publicKey,
                                                   @Mock PeerInformation peerInformation,
                                                   @Mock Path path) {
            underTest.setPeerInformationAndAddPathAndSetSuperPeer(publicKey, peerInformation, path);

            verify(eventConsumer).accept(new PeerDirectEvent(new Peer(publicKey)));
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
        void shouldRemoveChildrenAndPath(@Mock CompressedPublicKey publicKey,
                                         @Mock PeerInformation peerInformation,
                                         @Mock Path path) {
            peers.put(publicKey, peerInformation);
            paths.put(publicKey, path);

            underTest.removeChildrenAndPath(publicKey, path);

            assertThat(underTest.getPeers(), hasKey(publicKey));
            assertEquals(Set.of(), underTest.getChildrenKeys());
        }

        @Test
        @Disabled("not implemented")
        void shouldEmitPeerUnknownEventForChildren() {
            fail("not implemented (introduce PeerUnknownEvent?)");
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
        void shouldSetPeerInformationAndAddPathAndChildren(@Mock CompressedPublicKey publicKey,
                                                           @Mock PeerInformation peerInformation,
                                                           @Mock Path path) {
            underTest.setPeerInformationAndAddPathAndChildren(publicKey, peerInformation, path);

            assertThat(underTest.getPeers(), hasKey(publicKey));
            assertEquals(Set.of(publicKey), underTest.getChildrenKeys());
        }

        @Test
        void shouldEmitPeerDirectEventIfGivenPathIsTheFirstOneForThePeer(@Mock CompressedPublicKey publicKey,
                                                                         @Mock PeerInformation peerInformation,
                                                                         @Mock Path path) {
            underTest.setPeerInformationAndAddPathAndChildren(publicKey, peerInformation, path);

            verify(eventConsumer).accept(new PeerDirectEvent(new Peer(publicKey)));
        }

        @Test
        void shouldEmitNoEventIfGivenPathIsNotTheFirstOneForThePeer(@Mock CompressedPublicKey publicKey,
                                                                    @Mock PeerInformation peerInformation,
                                                                    @Mock Path path) {
            peers.put(publicKey, mock(PeerInformation.class));
            paths.put(publicKey, mock(Path.class));

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
