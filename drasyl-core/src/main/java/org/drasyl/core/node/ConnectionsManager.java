package org.drasyl.core.node;

import com.google.common.collect.HashMultimap;
import org.drasyl.core.node.connections.ConnectionComparator;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This class manages the available connections to all nodes.
 */
public class ConnectionsManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionsManager.class);
    private final ReadWriteLock lock;
    private final HashMultimap<Identity, PeerConnection> connections;
    private final Map<PeerConnection, Runnable> closeProcedures;

    public ConnectionsManager() {
        this(new ReentrantReadWriteLock(true), HashMultimap.create(), new HashMap<>());
    }

    ConnectionsManager(ReadWriteLock lock,
                       HashMultimap<Identity, PeerConnection> connections,
                       Map<PeerConnection, Runnable> closeProcedures) {
        this.lock = requireNonNull(lock);
        this.connections = requireNonNull(connections);
        this.closeProcedures = requireNonNull(closeProcedures);
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
    public void addConnection(PeerConnection connection, Runnable closeProcedure) {
        try {
            lock.writeLock().lock();

            LOG.debug("Add Connection '{}' for Node '{}'", connection, connection.getIdentity());

            Optional<PeerConnection> existingConnection = connections.get(connection.getIdentity()).stream().filter(c -> c.getClass() == connection.getClass()).findFirst();
            if (existingConnection.isPresent()) {
                LOG.debug("A Connection of this type already exists for Node '{}'. Replace and close existing Connection '{}' before adding new Connection '{}'", connection.getIdentity(), existingConnection.get(), connection);

                closeAndRemoveConnection(existingConnection.get());
            }

            connections.put(connection.getIdentity(), connection);
            closeProcedures.put(connection, closeProcedure);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Closes and removes <code>connection</code> from the list of available connections.
     *
     * @param connection the connection
     */
    public void closeConnection(PeerConnection connection) {
        try {
            lock.writeLock().lock();

            closeAndRemoveConnection(connection);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private void closeAndRemoveConnection(PeerConnection connection) {
        LOG.debug("Close and remove Connection '{}' for Node '{}'", connection, connection.getIdentity());
        connections.remove(connection.getIdentity(), connection);
        Optional.ofNullable(closeProcedures.remove(connection)).ifPresent(Runnable::run);
    }

    /**
     * Closes and removes all connections of type <code>clazz</code> for peer with identity
     * <code>identity</code>.
     *
     * @param identity the identity
     * @param clazz    the class
     */
    public void closeConnectionOfTypeForIdentity(Identity identity,
                                                 Class<? extends PeerConnection> clazz) {
        try {
            lock.writeLock().lock();

            Set<PeerConnection> connectionsOfType = connections.get(identity).stream().filter(c -> c.getClass() == clazz).collect(Collectors.toSet());
            for (PeerConnection peerConnection : connectionsOfType) {
                closeAndRemoveConnection(peerConnection);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Closes and removes all connections of type <code>clazz</code>.
     *
     * @param clazz the class
     */
    public void closeConnectionsOfType(Class<? extends PeerConnection> clazz) {
        try {
            lock.writeLock().lock();

            Set<PeerConnection> connectionsOfType = connections.values().stream().filter(c -> c.getClass() == clazz).collect(Collectors.toSet());
            for (PeerConnection peerConnection : connectionsOfType) {
                closeAndRemoveConnection(peerConnection);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
