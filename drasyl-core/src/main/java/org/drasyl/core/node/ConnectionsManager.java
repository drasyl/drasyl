package org.drasyl.core.node;

import org.drasyl.core.node.connections.ConnectionComparator;
import org.drasyl.core.node.connections.PeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
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
    private final Set<PeerConnection> connections;

    public ConnectionsManager() {
        this(new ReentrantReadWriteLock(true), new HashSet<>());
    }

    ConnectionsManager(ReadWriteLock lock,
                       Set<PeerConnection> connections) {
        this.lock = requireNonNull(lock);
        this.connections = requireNonNull(connections);
    }

    /**
     * Returns the best available connection to node with identity <code>identity</code>. If no
     * connection to node is available, <code>null</code> is returned.
     * <p>
     * The shortest connections (e.g. direct connection) are rated as best.
     *
     * @param identity
     * @return
     */
    public PeerConnection getConnection(Identity identity) {
        try {
            lock.readLock().lock();

            Optional<PeerConnection> connection = connections.stream().filter(c -> c.getIdentity().equals(identity)).min(ConnectionComparator.INSTANCE);

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
     * @param connection
     */
    public void addConnection(PeerConnection connection) {
        try {
            lock.writeLock().lock();

            LOG.debug("Add Connection '{}' for Node '{}'", connection, connection.getIdentity());

            Optional<PeerConnection> existingConnection = connections.stream().filter(c -> c.getIdentity().equals(connection.getIdentity()) && c.getClass() == connection.getClass()).findFirst();
            if (existingConnection.isPresent()) {
                LOG.debug("A Connection of this type already exists for Node '{}'. Replace and close existing Connection '{}' before adding new Connection '{}'", connection.getIdentity(), existingConnection.get(), connection);
                existingConnection.get().close();
            }

            connections.add(connection);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Closes and removes <code>connection</code> from the list of available connections.
     *
     * @param connection
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
        connections.remove(connection);
        connection.close();
    }

    /**
     * Closes and removes all connections of type <code>clazz</code> for peer with identity
     * <code>identity</code>.
     *
     * @param identity
     * @param clazz
     */
    public void closeConnectionOfTypeForIdentity(Identity identity,
                                                 Class<? extends PeerConnection> clazz) {
        try {
            lock.writeLock().lock();

            Set<PeerConnection> connectionsOfType = this.connections.stream().filter(c -> c.getIdentity().equals(identity) && c.getClass() == clazz).collect(Collectors.toSet());
            for (Iterator<PeerConnection> iterator = connectionsOfType.iterator();
                 iterator.hasNext(); ) {
                closeAndRemoveConnection(iterator.next());
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Closes and removes all connections of type <code>clazz</code>.
     *
     * @param clazz
     */
    public void closeConnectionsOfType(Class<? extends PeerConnection> clazz) {
        try {
            lock.writeLock().lock();

            Set<PeerConnection> connectionsOfType = this.connections.stream().filter(c -> c.getClass() == clazz).collect(Collectors.toSet());
            for (Iterator<PeerConnection> iterator = connectionsOfType.iterator();
                 iterator.hasNext(); ) {
                closeAndRemoveConnection(iterator.next());
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
