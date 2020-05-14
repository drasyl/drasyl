package org.drasyl.core.node;

import com.google.common.collect.HashMultimap;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionsManagerTest {
    private ReadWriteLock lock;
    private Lock readLock;
    private Lock writeLock;
    private HashMultimap<Identity, PeerConnection> connections;
    private Map<PeerConnection, Runnable> closeProcedures;
    private Identity identity;
    private PeerConnection peerConnection;
    private PeerConnection peerConnection2;
    private Runnable runnable1;
    private Runnable runnable2;

    @BeforeEach
    void setUp() {
        lock = mock(ReadWriteLock.class);
        readLock = mock(Lock.class);
        writeLock = mock(Lock.class);
        connections = HashMultimap.create();
        closeProcedures = mock(Map.class);
        identity = Identity.of(Crypto.randomString(5));
        peerConnection = mock(PeerConnection.class);
        peerConnection2 = mock(PeerConnection.class);
        runnable1 = mock(Runnable.class);
        runnable2 = mock(Runnable.class);

        when(lock.writeLock()).thenReturn(writeLock);
        when(lock.readLock()).thenReturn(readLock);

        when(peerConnection.getIdentity()).thenReturn(identity);
        when(peerConnection2.getIdentity()).thenReturn(identity);
        when(closeProcedures.remove(eq(peerConnection))).thenReturn(runnable1);
        when(closeProcedures.remove(eq(peerConnection2))).thenReturn(runnable2);
    }

    @Test
    void getConnectionShouldReturnBestConnectionForGivenIdentity() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);

        assertEquals(peerConnection, connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void getConnectionShouldReturnNullIfNoConnectionIsAvailable() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, HashMultimap.create(), Map.of());

        assertNull(connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void addConnectionShouldCloseExistingConnectionAndAddConnectionToListOfAllConnections() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.addConnection(peerConnection2, runnable2);

        verify(runnable1).run();
        assertTrue(connections.containsEntry(identity, peerConnection2));
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionShouldCloseAndRemoveFromListOfAllConnections() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnection(peerConnection);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).run();
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionOfTypeForIdentityShouldCloseAndRemoveAllConnectionsOfGivenTypeForGivenIdentity() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnectionOfTypeForIdentity(identity, peerConnection.getClass());

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).run();
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionsOfTypeShouldCloseAndRemoveAllConnectionsOfGivenType() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnectionsOfType(peerConnection.getClass());

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).run();
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }
}