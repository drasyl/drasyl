package org.drasyl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeersManagerTest {
    private ReadWriteLock lock;
    private Lock readLock;
    private Lock writeLock;
    private Map<Identity, PeerInformation> peers;
    private Set<Identity> children;
    private Identity superPeer;
    private Identity identity;
    private PeerInformation peer;

    @BeforeEach
    void setUp() {
        lock = mock(ReadWriteLock.class);
        readLock = mock(Lock.class);
        writeLock = mock(Lock.class);
        peers = mock(Map.class);
        children = mock(Set.class);
        superPeer = mock(Identity.class);
        identity = mock(Identity.class);
        peer = mock(PeerInformation.class);

        when(lock.writeLock()).thenReturn(writeLock);
        when(lock.readLock()).thenReturn(readLock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getPeersShouldReturnAnImmutableListOfAllPeers() {
        PeersManager manager = new PeersManager(lock, Map.of(identity, peer), children, superPeer);

        Map<Identity, PeerInformation> peers = manager.getPeers();

        assertThat(peers, instanceOf(ImmutableMap.class));
        assertThat(peers, hasEntry(identity, peer));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void isPeerShouldReturnTrueIfGivenIdentityIsAKnownPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(identity, peer), children, superPeer);

        assertTrue(manager.isPeer(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void isPeerShouldReturnFalseIfGivenIdentityIsAUnknownPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(), children, superPeer);

        assertFalse(manager.isPeer(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void addPeerShouldAddPeerToListOfKnownPeers() {
        PeersManager manager = new PeersManager(lock, peers, children, superPeer);

        manager.addPeer(identity, peer);

        verify(peers).put(identity, peer);
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void addPeersShouldAddGivenPeersToListOfKnownPeers() {
        PeersManager manager = new PeersManager(lock, peers, children, superPeer);

        manager.addPeers(Map.of(identity, peer));

        verify(peers).putAll(Map.of(identity, peer));
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void removePeerShouldRemovePeerFromListOfKnownPeers() {
        PeersManager manager = new PeersManager(lock, peers, children, superPeer);

        manager.removePeer(identity);

        verify(peers).remove(identity);
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void removePeerShouldThrowExceptionIfGivenPeerIsSuperPeer() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeersManager manager = new PeersManager(lock, peers, children, superPeer);

            manager.removePeer(superPeer);
        });
    }

    @Test
    void removePeerShouldThrowExceptionIfGivenPeerIsChildren() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeersManager manager = new PeersManager(lock, peers, Set.of(identity), superPeer);

            manager.removePeer(identity);
        });
    }

    @Test
    void removePeersShouldRemoveGivenPeersFromListOfKnownPeers() {
        PeersManager manager = new PeersManager(lock, peers, children, superPeer);

        manager.removePeers(identity);

        verify(peers).remove(identity);
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void removePeersShouldThrowExceptionIfGivenPeersContainSuperPeer() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeersManager manager = new PeersManager(lock, peers, children, superPeer);

            manager.removePeers(superPeer);
        });
    }

    @Test
    void removePeersShouldThrowExceptionIfGivenPeersContainChildren() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeersManager manager = new PeersManager(lock, peers, Set.of(identity), superPeer);

            manager.removePeers(identity);
        });
    }

    @Test
    void getChildrenShouldReturnAnImmutableSetOfAllChildren() {
        PeersManager manager = new PeersManager(lock, peers, Set.of(identity), superPeer);

        Set<Identity> children = manager.getChildren();

        assertThat(children, instanceOf(ImmutableSet.class));
        assertThat(children, hasItem(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void isChildrenShouldTrueIfGivenIdentityIsAChildren() {
        PeersManager manager = new PeersManager(lock, peers, Set.of(identity), superPeer);

        assertTrue(manager.isChildren(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void isChildrenShouldFalseIfGivenIdentityIsNotAChildren() {
        PeersManager manager = new PeersManager(lock, peers, Set.of(), superPeer);

        assertFalse(manager.isChildren(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void addChildrenShouldAddChildrenToListOfChildren() {
        PeersManager manager = new PeersManager(lock, Map.of(identity, peer), children, superPeer);

        manager.addChildren(identity);

        verify(children).addAll(List.of(identity));
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void addChildrenShouldThrowExceptionWhenChildrenIsNotKnown() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeersManager manager = new PeersManager(lock, Map.of(), children, superPeer);

            manager.addChildren(identity);
        });
    }

    @Test
    void removeChildrenShouldRemoveChildrenFromListOfChildren() {
        PeersManager manager = new PeersManager(lock, peers, children, superPeer);

        manager.removeChildren(identity);

        verify(children).removeAll(List.of(identity));
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void getPeerShouldReturnInformationOfGivenPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(identity, peer), children, superPeer);

        assertEquals(peer, manager.getPeer(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void getSuperPeerShouldReturnIdentityAndInformationOfTheSuperPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(superPeer, peer), children, superPeer);

        assertEquals(Pair.of(superPeer, peer), manager.getSuperPeer());
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void setSuperPeerShouldSetGivenIdentityAsSuperPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(superPeer, peer), children, null);

        manager.setSuperPeer(superPeer);

        assertEquals(Pair.of(superPeer, peer), manager.getSuperPeer());
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void setSuperPeerShouldThrowExceptionWhenSuperPeerIsNotKnown() {
        assertThrows(IllegalArgumentException.class, () -> {
            PeersManager manager = new PeersManager(lock, Map.of(), children, superPeer);

            manager.setSuperPeer(identity);
        });
    }

    @Test
    void isSuperPeerShouldReturnTrueIfGivenIdentityIsNotTheSuperPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(superPeer, peer), children, superPeer);

        assertTrue(manager.isSuperPeer(superPeer));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void isSuperPeerShouldReturnFalseIfGivenIdentityIsNotTheSuperPeer() {
        PeersManager manager = new PeersManager(lock, Map.of(superPeer, peer), children, null);

        assertFalse(manager.isSuperPeer(superPeer));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void unsetSuperPeerShouldSetSuperPeerToNull() {
        PeersManager manager = new PeersManager(lock, Map.of(superPeer, peer), children, superPeer);

        manager.unsetSuperPeer();

        assertNull(manager.getSuperPeer());
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }
}