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

import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.drasyl.peer.connection.superpeer.SuperPeerConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class ConnectionComparatorTest {
    private PeerConnection peerConnection;
    private AbstractNettyConnection nettyPeerConnection;
    private NodeServerClientConnection clientConnection;
    private SuperPeerConnection superPeerConnection;
    private LoopbackPeerConnection loopbackPeerConnection;

    @BeforeEach
    void setUp() {
        peerConnection = mock(PeerConnection.class);
        nettyPeerConnection = mock(AbstractNettyConnection.class);
        clientConnection = mock(NodeServerClientConnection.class);
        superPeerConnection = mock(SuperPeerConnection.class);
        loopbackPeerConnection = mock(LoopbackPeerConnection.class);
    }

    @Test
    void compare() {
        ArrayList<PeerConnection> peerConnections = new ArrayList<>();
        peerConnections.add(peerConnection);
        peerConnections.add(nettyPeerConnection);
        peerConnections.add(loopbackPeerConnection);
        peerConnections.add(clientConnection);
        peerConnections.add(superPeerConnection);

        peerConnections.sort(ConnectionComparator.INSTANCE);

        assertEquals(List.of(loopbackPeerConnection, clientConnection, superPeerConnection, nettyPeerConnection, peerConnection), peerConnections);
        assertNotEquals(List.of(peerConnection, nettyPeerConnection, superPeerConnection, clientConnection, loopbackPeerConnection), peerConnections);
    }

    @Test
    void selfConnectionShouldBeTheFirstOption() {
        assertEquals(-1, ConnectionComparator.INSTANCE.compare(loopbackPeerConnection, peerConnection));
        assertEquals(-1, ConnectionComparator.INSTANCE.compare(loopbackPeerConnection, nettyPeerConnection));
        assertEquals(-1, ConnectionComparator.INSTANCE.compare(loopbackPeerConnection, clientConnection));
        assertEquals(-1, ConnectionComparator.INSTANCE.compare(loopbackPeerConnection, superPeerConnection));
    }

    @Test
    void peerConnectionShouldBeTheLastOption() {
        assertEquals(1, ConnectionComparator.INSTANCE.compare(peerConnection, nettyPeerConnection));
        assertEquals(1, ConnectionComparator.INSTANCE.compare(peerConnection, clientConnection));
        assertEquals(1, ConnectionComparator.INSTANCE.compare(peerConnection, superPeerConnection));
        assertEquals(1, ConnectionComparator.INSTANCE.compare(peerConnection, loopbackPeerConnection));
    }
}