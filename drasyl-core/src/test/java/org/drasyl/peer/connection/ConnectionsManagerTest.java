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

package org.drasyl.peer.connection;

import com.google.common.collect.HashMultimap;
import org.drasyl.crypto.Crypto;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.PeerConnection.CloseReason;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

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
    private Identity superPeer;
    private SuperPeerClientConnection superPeerClientConnection;
    private Consumer<CloseReason> superPeerRunnable;

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
        superPeerClientConnection = mock(SuperPeerClientConnection.class);
        reason = CloseReason.REASON_SHUTTING_DOWN;
        runnable1 = mock(Consumer.class);
        runnable2 = mock(Consumer.class);
        superPeerRunnable = mock(Consumer.class);
        superPeer = mock(Identity.class);

        when(lock.writeLock()).thenReturn(writeLock);
        when(lock.readLock()).thenReturn(readLock);

        when(peerConnection.getIdentity()).thenReturn(identity);
        when(peerConnection2.getIdentity()).thenReturn(identity);
        when(superPeerClientConnection.getIdentity()).thenReturn(superPeer);
        when(closeProcedures.remove(eq(peerConnection))).thenReturn(runnable1);
        when(closeProcedures.remove(eq(peerConnection2))).thenReturn(runnable2);
        when(closeProcedures.remove(eq(superPeerClientConnection))).thenReturn(superPeerRunnable);
    }

    @Test
    void getConnectionShouldReturnDirectConnectionIfItsTheBestConnectionForGivenIdentity() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures, superPeer);
        connectionsManager.addConnection(peerConnection, runnable1);

        Assertions.assertEquals(peerConnection, connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void getConnectionShouldReturnSuperPeerConnectionIfItsTheBestConnectionForGivenIdentity() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures, superPeer);
        connectionsManager.addConnection(superPeerClientConnection, superPeerRunnable);

        Assertions.assertEquals(superPeerClientConnection, connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void getConnectionShouldReturnNullIfNoConnectionIsAvailable() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, HashMultimap.create(), Map.of(), null);

        assertNull(connectionsManager.getConnection(identity));
        verify(readLock).lock();
        verify(readLock).unlock();
    }

    @Test
    void addConnectionShouldCloseExistingConnectionAndAddConnectionToListOfAllConnections() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures, null);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.addConnection(peerConnection2, runnable2);

        verify(runnable1).accept(CloseReason.REASON_NEW_SESSION);
        assertTrue(connections.containsEntry(identity, peerConnection2));
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionShouldCloseAndRemoveFromListOfAllConnections() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures, null);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnection(peerConnection, reason);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).accept(reason);
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionOfTypeForIdentityShouldCloseAndRemoveAllConnectionsOfGivenTypeForGivenIdentity() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures, null);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnectionOfTypeForIdentity(identity, peerConnection.getClass(), reason);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).accept(reason);
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }

    @Test
    void closeConnectionsOfTypeShouldCloseAndRemoveAllConnectionsOfGivenType() {
        ConnectionsManager connectionsManager = new ConnectionsManager(lock, connections, closeProcedures, null);
        connectionsManager.addConnection(peerConnection, runnable1);
        connectionsManager.closeConnectionsOfType(peerConnection.getClass(), reason);

        assertFalse(connections.containsEntry(identity, peerConnection));
        verify(runnable1).accept(reason);
        verify(writeLock, times(2)).lock();
        verify(writeLock, times(2)).unlock();
    }
}