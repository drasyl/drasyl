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
import org.drasyl.identity.Identity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeersManagerTest {
    @Mock
    private ReadWriteLock lock;
    @Mock
    private Map<Identity, PeerInformation> peers;
    @Mock
    private Set<Identity> children;
    @Mock
    private Map<Identity, Identity> grandchildren;
    @Mock
    private Identity identity;
    @Mock
    private Identity superPeer;
    @Mock
    private Lock writeLock;
    @Mock
    private Lock readLock;
    @Mock
    private Consumer<Event> eventConsumer;
    private PeersManager underTest;

    @BeforeEach
    void setUp() {
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
        private Identity identity;
        @Mock
        private PeerInformation peerInformation;
        @Mock
        private PeerInformation existingInformation;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
            when(peers.computeIfAbsent(eq(identity), any())).thenReturn(existingInformation);
        }

        @Test
        void shouldAddInformation() {
            underTest.addPeerInformation(identity, peerInformation);

            verify(existingInformation).add(peerInformation);
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
        private Identity identity;
        @Mock
        private PeerInformation peerInformation;
        @Mock
        private PeerInformation existingInformation;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
            when(peers.computeIfAbsent(eq(identity), any())).thenReturn(existingInformation);
        }

        @Test
        void shouldAddInformation() {
            underTest.removePeerInformation(identity, peerInformation);

            verify(existingInformation).remove(peerInformation);
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
            assertEquals(Set.of(), underTest.getChildren());
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
        private Identity identity;

        @BeforeEach
        void setup() {
            when(lock.readLock()).thenReturn(readLock);
        }

        @Test
        void shouldReturnTrueForChildren() {
            when(children.contains(identity)).thenReturn(true);

            assertTrue(underTest.isChildren(identity));
        }

        @Test
        void shouldReturnFalseForNonChildren() {
            when(children.contains(identity)).thenReturn(false);

            assertFalse(underTest.isChildren(identity));
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
        private Identity identity;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
        }

        @Test
        void shouldAddChildren() {
            underTest.addChildren(identity);

            verify(children).addAll(List.of(identity));
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
        private Identity identity;

        @BeforeEach
        void setup() {
            when(lock.writeLock()).thenReturn(writeLock);
        }

        @Test
        void shouldRemoveChildren() {
            underTest.removeChildren(identity);

            verify(children).removeAll(List.of(identity));
        }

        @AfterEach
        void tearDown() {
            verify(writeLock).lock();
            verify(writeLock).unlock();
        }
    }
}
