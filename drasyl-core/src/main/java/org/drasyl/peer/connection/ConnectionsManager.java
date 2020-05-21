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
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.PeerConnection.CloseReason;
import org.drasyl.peer.connection.server.NodeServerConnection;
import org.drasyl.peer.connection.superpeer.SuperPeerClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.peer.connection.PeerConnection.CloseReason.REASON_NEW_SESSION;

/**
 * This class manages the available connections to all nodes.
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class ConnectionsManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionsManager.class);
    private final ReadWriteLock lock;
    private final HashMultimap<Identity, PeerConnection> connections;
    private final Map<PeerConnection, Consumer<CloseReason>> closeProcedures;
    private Identity superPeer;

    public ConnectionsManager() {
        this(new ReentrantReadWriteLock(true), HashMultimap.create(), new HashMap<>(), null);
    }

    public ConnectionsManager(ReadWriteLock lock,
                              HashMultimap<Identity, PeerConnection> connections,
                              Map<PeerConnection, Consumer<CloseReason>> closeProcedures,
                              Identity superPeer) {
        this.lock = requireNonNull(lock);
        this.connections = requireNonNull(connections);
        this.closeProcedures = requireNonNull(closeProcedures);
        this.superPeer = superPeer;
    }

    /**
     * Returns the best available connection to node with identity <code>identity</code>. If no
     * connection to node is available, <code>null</code> is returned.
     * <p>
     * The shortest connections (e.g. direct connection) are rated as best.
     *
     * @param identity the identity
     */
    public PeerConnection getConnection(Identity identity) {
        try {
            lock.readLock().lock();

            Optional<PeerConnection> connection = connections.get(identity).stream().min(ConnectionComparator.INSTANCE);

            // super peer fallback
            if (!connection.isPresent() && superPeer != null) {
                connection = connections.get(superPeer).stream().findFirst();
            }

            return connection.orElse(null);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Adds <code>connection</code> to the list of available connections.
     * <p>
     * This method will replace & close existing equal (not same) connections. Two connections are
     * considered to be " equal" if the node at the other end of the connection and the type of the
     * connection (client, super peer, p2p) are the same.
     *
     * @param connection     the connection
     * @param closeProcedure the procedure to close this connection
     */
    public void addConnection(PeerConnection connection,
                              Consumer<CloseReason> closeProcedure) {
        try {
            lock.writeLock().lock();

            LOG.debug("Add Connection '{}' for Node '{}'", connection, connection.getIdentity());

            // remember super peer identity for fast lookup
            if (connection instanceof SuperPeerClientConnection) {
                superPeer = connection.getIdentity();
            }

            Optional<PeerConnection> existingConnection = connections.get(connection.getIdentity()).stream().filter(c -> c.getClass() == connection.getClass()).findFirst();
            if (existingConnection.isPresent()) {
                LOG.debug("A Connection of this type already exists for Node '{}'. Replace and close existing Connection '{}' before adding new Connection '{}'", connection.getIdentity(), existingConnection.get(), connection);

                closeAndRemoveConnection(existingConnection.get(), REASON_NEW_SESSION);
            }

            connections.put(connection.getIdentity(), connection);
            closeProcedures.put(connection, closeProcedure);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void removeConnection(PeerConnection connection) {
        connections.remove(connection.getIdentity(), connection);
    }

    private void closeAndRemoveConnection(PeerConnection connection,
                                          CloseReason reason) {
        removeConnection(connection);
        Optional.ofNullable(closeProcedures.remove(connection)).ifPresent(p -> {
            p.accept(reason);
            LOG.debug("Close and remove Connection '{}' for Node '{}' for Reason '{}'", connection, connection.getIdentity(), reason);
        });
    }

    /**
     * Closes and removes <code>connection</code> from the list of available connections.
     * <code>reason</code> is sent to the connected peers as the reason for the closure.
     *
     * @param connection the connection
     * @param reason     reason why this connection is closed
     */
    public void closeConnection(PeerConnection connection,
                                CloseReason reason) {
        try {
            lock.writeLock().lock();

            closeAndRemoveConnection(connection, reason);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes <code>connection</code> from the list of available connections.
     *
     * @param connection the connection
     */
    public void removeClosingConnection(PeerConnection connection) {
        try {
            lock.writeLock().lock();

            removeConnection(connection);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Closes and removes all connections of type <code>clazz</code> for peer with identity
     * <code>identity</code>. <code>reason</code> is sent to the connected peers as the reason for
     * the closure.
     *
     * @param identity the identity
     * @param clazz    the class
     * @param reason   reason why this connection is closed
     */
    public void closeConnectionOfTypeForIdentity(Identity identity,
                                                 Class<? extends PeerConnection> clazz,
                                                 CloseReason reason) {
        try {
            lock.writeLock().lock();

            Set<PeerConnection> connectionsOfType = connections.get(identity).stream().filter(c -> c.getClass() == clazz).collect(Collectors.toSet());
            for (PeerConnection peerConnection : connectionsOfType) {
                closeAndRemoveConnection(peerConnection, reason);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Closes and removes all connections of type <code>clazz</code>. <code>reason</code> is sent to
     * the connected peers as the reason for the closure.
     *
     * @param clazz  the class
     * @param reason reason why this connection is closed
     */
    public void closeConnectionsOfType(Class<? extends PeerConnection> clazz,
                                       CloseReason reason) {
        try {
            lock.writeLock().lock();

            Set<PeerConnection> connectionsOfType = connections.values().stream().filter(c -> c.getClass() == clazz).collect(Collectors.toSet());
            for (PeerConnection peerConnection : connectionsOfType) {
                closeAndRemoveConnection(peerConnection, reason);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
