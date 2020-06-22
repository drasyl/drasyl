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

import com.fasterxml.jackson.core.JsonProcessingException;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
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
    private Map<CompressedPublicKey, PeerInformation> peers;
    @Mock
    private Set<CompressedPublicKey> children;
    @Mock
    private Map<CompressedPublicKey, CompressedPublicKey> grandchildren;
    @Mock
    private CompressedPublicKey publicKey;
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
        private CompressedPublicKey identity;
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
        private CompressedPublicKey identity;
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
        private CompressedPublicKey identity;

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
        private CompressedPublicKey identity;

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
        private CompressedPublicKey identity;

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

    @Nested
    class ToJson {
        @BeforeEach
        void setup() throws CryptoException {
            when(lock.readLock()).thenReturn(readLock);

            publicKey = CompressedPublicKey.of("022910262d4b1b4681055d4d6ed047ed6c35d7a55e8bcbbbb5528a8a40414991ac");
            PeerInformation peerInformation = PeerInformation.of();

            underTest = new PeersManager(lock, Map.of(publicKey, peerInformation), Set.of(publicKey), Map.of(), null, eventConsumer);
        }

        @Test
        void shouldProduceCorrectJsonObject() throws JsonProcessingException {
            assertThatJson(JACKSON_WRITER.writeValueAsString(underTest))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo("{\"peers\":[[\"022910262d4b1b4681055d4d6ed047ed6c35d7a55e8bcbbbb5528a8a40414991ac\", {\"endpoints\":[]}]],\"children\":[[\"022910262d4b1b4681055d4d6ed047ed6c35d7a55e8bcbbbb5528a8a40414991ac\", {\"endpoints\":[]}]],\"grandchildrenRoutes\":[],\"superPeer\":null}");
        }
    }
}
