package org.drasyl.core.node;

import com.google.common.collect.HashMultimap;
import org.drasyl.core.node.connections.PeerConnection.CloseReason;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import static org.drasyl.core.node.connections.PeerConnection.CloseReason.REASON_NEW_SESSION;
import static org.drasyl.core.node.connections.PeerConnection.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionsManagerTest {
    private ReadWriteLock lock;
    private Lock readLock;
    private Lock writeLock;
    private HashMultimap<Identity, PeerConnection> connections;
    private Map<PeerConnection, Consumer<CloseReason>> closeProcedures;
    private Identity identity;
    private PeerConnection peerConnection;
    private PeerConnection peerConnection2;
    private CloseReason reason;
    private Consumer<CloseReason> runnable1;
    private Consumer<CloseReason> runnable2;

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
        reason = REASON_SHUTTING_DOWN;
        runnable1 = mock(Consumer.class);
        runnable2 = mock(Consumer.class);

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

        verify(runnable1).accept(REASON_NEW_SESSION);
        assertTrue(connections.containsEntry(identity, peerConnection2));
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionShouldCloseAndRemoveFromListOfAllConnections() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnection(peerConnection, reason);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).accept(reason);
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionOfTypeForIdentityShouldCloseAndRemoveAllConnectionsOfGivenTypeForGivenIdentity() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnectionOfTypeForIdentity(identity, peerConnection.getClass(), reason);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).accept(reason);
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionsOfTypeShouldCloseAndRemoveAllConnectionsOfGivenType() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnectionsOfType(peerConnection.getClass(), reason);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).accept(reason);
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }
}