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

import org.drasyl.event.Event;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.event.PeerUnreachableEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock
    private ReadWriteLock lock;
    private Map<CompressedPublicKey, PeerInformation> peers;
    private Set<CompressedPublicKey> children;
    private Map<CompressedPublicKey, CompressedPublicKey> grandchildren;
    @Mock
    private CompressedPublicKey superPeer;
    @Mock
    private Lock writeLock;
    @Mock
    private Lock readLock;
    @Mock
    private Consumer<Event> eventConsumer;
    private PeersManager underTest;

    @BeforeEach
    void setUp() {
        peers = new HashMap<>();
        children = new HashSet<>();
        grandchildren = new HashMap<>();
        underTest = new PeersManager(lock, peers, children, grandchildren, superPeer, eventConsumer);
    }

    @Nested
    class GetPeers {
        @BeforeEach
        void setup() {
            when(lock.readLock()).thenReturn(readLock);
        }

        @Test
        void shouldReturnPeers() {
            assertEquals(Map.of(), underTest.getPeers());
        }

        @AfterEach
        void tearDown() {
            verify(readLock).lock();
            verify(readLock).unlock();
        }
    }

    @Nested
    class AddPeerInformation {
        @Mock
        private CompressedPublicKey publicKey;
        @Mock
        private PeerInformation peerInformation;
        @Mock
        private PeerInformation existingInformation;
        @Mock
        private Path path;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
        }

        @Test
        void shouldAddInformation() {
            peers.put(publicKey, existingInformation);
            when(existingInformation.add(any())).thenReturn(existingInformation);

            underTest.addPeerInformation(publicKey, peerInformation);

            verify(existingInformation).add(peerInformation);
        }

        @Test
        void shouldEmitPeerDirectEventIfFirstPathHasBeenAdded() {
            when(peerInformation.getPaths()).thenReturn(Set.of(path));

            underTest.addPeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerDirectEvent(new Peer(publicKey)));
        }

        @Test
        void shouldEmitPeerRelayEventIfPeerInformationWithNoPathsHasBeenCreated() {
            underTest.addPeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerRelayEvent(new Peer(publicKey)));
        }

        @AfterEach
        void tearDown() {
            verify(writeLock).lock();
            verify(writeLock).unlock();
        }
    }

    @Nested
    class RemovePeerInformation {
        @Mock
        private CompressedPublicKey publicKey;
        @Mock
        private PeerInformation peerInformation;
        @Mock(answer = Answers.RETURNS_DEEP_STUBS)
        private PeerInformation existingInformation;
        @Mock
        private Path path;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
        }

        @Test
        void shouldRemoveInformation() {
            peers.put(publicKey, existingInformation);

            underTest.removePeerInformation(publicKey, peerInformation);

            verify(existingInformation).remove(peerInformation);
        }

        @Test
        void shouldEmitPeerUnreachableEventIfNoPathsLeftAndNoSuperPeerExists() {
            underTest = new PeersManager(lock, peers, children, grandchildren, null, eventConsumer);

            peers.put(publicKey, existingInformation);
            when(existingInformation.getPaths().size()).thenReturn(1).thenReturn(0);

            underTest.removePeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerUnreachableEvent(new Peer(publicKey)));
        }

        @Test
        void shouldEmitPeerRelayEventIfNoPathsLeftAndSuperPeerExists() {
            underTest = new PeersManager(lock, peers, children, grandchildren, superPeer, eventConsumer);

            peers.put(publicKey, existingInformation);
            when(existingInformation.getPaths().size()).thenReturn(1).thenReturn(0);

            underTest.removePeerInformation(publicKey, peerInformation);

            verify(eventConsumer).accept(new PeerRelayEvent(new Peer(publicKey)));
        }

        @AfterEach
        void tearDown() {
            verify(writeLock).lock();
            verify(writeLock).unlock();
        }
    }

    @Nested
    class GetChildren {
        @BeforeEach
        void setup() {
            when(lock.readLock()).thenReturn(readLock);

            underTest = new PeersManager(lock, peers, Set.of(), grandchildren, superPeer, eventConsumer);
        }

        @Test
        void shouldReturnChildren() {
            assertEquals(Map.of(), underTest.getChildren());
        }

        @AfterEach
        void tearDown() {
            verify(readLock).lock();
            verify(readLock).unlock();
        }
    }

    @Nested
    class IsChildren {
        @Mock
        private CompressedPublicKey publicKey;

        @BeforeEach
        void setup() {
            when(lock.readLock()).thenReturn(readLock);
        }

        @Test
        void shouldReturnTrueForChildren() {
            children.add(publicKey);

            assertTrue(underTest.isChildren(publicKey));
        }

        @Test
        void shouldReturnFalseForNonChildren() {
            assertFalse(underTest.isChildren(publicKey));
        }

        @AfterEach
        void tearDown() {
            verify(readLock).lock();
            verify(readLock).unlock();
        }
    }

    @Nested
    class AddChildren {
        @Mock
        private CompressedPublicKey publicKey;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
        }

        @Test
        void shouldAddChildren() {
            underTest.addChildren(publicKey);

            assertTrue(children.contains(publicKey));
        }

        @AfterEach
        void tearDown() {
            verify(writeLock).lock();
            verify(writeLock).unlock();
        }
    }

    @Nested
    class RemoveChildren {
        @Mock
        private CompressedPublicKey identity;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
        }

        @Test
        void shouldRemoveChildren() {
            underTest.removeChildren(identity);

            assertFalse(children.contains(identity));
        }

        @AfterEach
        void tearDown() {
            verify(writeLock).lock();
            verify(writeLock).unlock();
        }
    }
}
