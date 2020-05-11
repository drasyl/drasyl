package org.drasyl.core.node;

import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

class ConnectionsManagerTest {
    private ReadWriteLock lock;
    private Lock readLock;
    private Lock writeLock;
    private Set<PeerConnection> connections;
    private Identity identity;
    private PeerConnection peerConnection;
    private PeerConnection peerConnection2;
    private Stream<PeerConnection> stream;

    @BeforeEach
    void setUp() {
        lock = mock(ReadWriteLock.class);
        readLock = mock(Lock.class);
        writeLock = mock(Lock.class);
        connections = mock(Set.class);
        identity = mock(Identity.class);
        peerConnection = mock(PeerConnection.class);
        peerConnection2 = mock(PeerConnection.class);
        stream = mock(Stream.class);

        when(lock.writeLock()).thenReturn(writeLock);
        when(lock.readLock()).thenReturn(readLock);
    }

    @Test
    void getConnectionShouldReturnBestConnectionForGivenIdentity() {
        when(peerConnection.getIdentity()).thenReturn(identity);

        ConnectionsManager connectionsManager = new ConnectionsManager(lock, Set.of(peerConnection));

        assertEquals(peerConnection, connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void getConnectionShouldReturnNullIfNoConnectionIsAvailable() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, Set.of());

        assertNull(connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void addConnectionShouldCloseExistingConnectionAndAddConnectionToListOfAllConnections() {
        when(connections.stream()).thenReturn(stream);
        when(stream.filter(any())).thenReturn(stream);
        when(stream.findFirst()).thenReturn(Optional.of(peerConnection));

        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections);
        connectionsManager.addConnection(peerConnection2);

        verify(peerConnection).close();
        verify(connections).add(peerConnection2);
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void closeConnectionShouldCloseAndRemoveFromListOfAllConnections() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections);
        connectionsManager.closeConnection(peerConnection);

        verify(connections).remove(peerConnection);
        verify(peerConnection).close();
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void closeConnectionOfTypeForIdentityShouldCloseAndRemoveAllConnectionsOfGivenTypeForGivenIdentity() {
        when(connections.stream()).thenReturn(stream);
        when(stream.filter(any())).thenReturn(stream);
        when(stream.collect(any())).thenReturn(Set.of(peerConnection));

        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections);
        connectionsManager.closeConnectionOfTypeForIdentity(identity, ClientConnection.class);

        verify(connections).remove(peerConnection);
        verify(peerConnection).close();
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }

    @Test
    void closeConnectionsOfTypeShouldCloseAndRemoveAllConnectionsOfGivenType() {
        when(connections.stream()).thenReturn(stream);
        when(stream.filter(any())).thenReturn(stream);
        when(stream.collect(any())).thenReturn(Set.of(peerConnection));

        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections);
        connectionsManager.closeConnectionsOfType(PeerConnection.class);

        verify(connections).remove(peerConnection);
        verify(peerConnection).close();
        verify(writeLock).lock();
        verify(writeLock).unlock();
    }
}