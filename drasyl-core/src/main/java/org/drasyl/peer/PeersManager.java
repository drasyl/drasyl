/*
 * Copyright (c) 2020-2021.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the public keys, available
 * interfaces, connections or relations (e.g. direct/relayed connection, super peer, child). Before
 * a relation is set for a peer, it must be ensured that its information is available. Likewise, the
 * information may not be removed from a peer if the peer still has a relation
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeersManager {
    private final ReadWriteLock lock;
    private final Set<CompressedPublicKey> peers;
    private final SetMultimap<CompressedPublicKey, Object> paths;
    private final Set<CompressedPublicKey> children;
    private final Consumer<Event> eventConsumer;
    private final Identity identity;
    private CompressedPublicKey superPeer;

    public PeersManager(final Consumer<Event> eventConsumer, final Identity identity) {
        this(new ReentrantReadWriteLock(true), new HashSet<>(), HashMultimap.create(), new HashSet<>(), null, eventConsumer, identity);
    }

    @SuppressWarnings("java:S2384")
    PeersManager(final ReadWriteLock lock,
                 final Set<CompressedPublicKey> peers,
                 final SetMultimap<CompressedPublicKey, Object> paths,
                 final Set<CompressedPublicKey> children,
                 final CompressedPublicKey superPeer,
                 final Consumer<Event> eventConsumer,
                 final Identity identity) {
        this.lock = lock;
        this.peers = peers;
        this.paths = paths;
        this.children = children;
        this.superPeer = superPeer;
        this.eventConsumer = eventConsumer;
        this.identity = identity;
    }

    @Override
    public String toString() {
        try {
            lock.readLock().lock();

            return "PeersManager{" +
                    "peers=" + peers +
                    ", paths=" + paths +
                    ", children=" + children +
                    ", eventConsumer=" + eventConsumer +
                    ", superPeer=" + superPeer +
                    '}';
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<CompressedPublicKey> getPeers() {
        try {
            lock.readLock().lock();

            return Set.copyOf(peers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<CompressedPublicKey> getChildren() {
        try {
            lock.readLock().lock();

            // It is necessary to create a new HashMap because otherwise, this can raise a
            // ConcurrentModificationException.
            // See: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/77
            return Set.copyOf(children);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return public key  of Super Peer. If no Super Peer is defined, then
     * <code>null</code> is returned
     */
    public CompressedPublicKey getSuperPeer() {
        try {
            lock.readLock().lock();

            return superPeer;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<Object> getPaths(final CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            return Set.copyOf(paths.get(publicKey));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addPath(final CompressedPublicKey publicKey,
                        final Object path) {
        requireNonNull(publicKey);

        try {
            lock.writeLock().lock();

            peers.add(publicKey);
            final boolean firstPath = paths.get(publicKey).isEmpty();
            paths.put(publicKey, path);

            if (firstPath) {
                eventConsumer.accept(new PeerDirectEvent(Peer.of(publicKey)));
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removePath(final CompressedPublicKey publicKey, final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
                eventConsumer.accept(new PeerRelayEvent(Peer.of(publicKey)));
                peers.remove(publicKey);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void unsetSuperPeerAndRemovePath(final Object path) {
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            if (superPeer != null) {
                eventConsumer.accept(new NodeOfflineEvent(Node.of(identity)));

                if (paths.remove(superPeer, path) && paths.get(superPeer).isEmpty()) {
                    eventConsumer.accept(new PeerRelayEvent(Peer.of(superPeer)));
                    peers.remove(superPeer);
                }

                superPeer = null;
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPathAndSetSuperPeer(final CompressedPublicKey publicKey,
                                       final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            peers.add(publicKey);
            final boolean firstPath = paths.get(publicKey).isEmpty();
            paths.put(publicKey, path);

            if (firstPath) {
                eventConsumer.accept(new PeerDirectEvent(Peer.of(publicKey)));
            }

            if (superPeer == null) {
                eventConsumer.accept(new NodeOnlineEvent(Node.of(identity)));
            }
            superPeer = publicKey;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChildrenAndPath(final CompressedPublicKey publicKey,
                                      final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
                eventConsumer.accept(new PeerRelayEvent(Peer.of(publicKey)));
                peers.remove(publicKey);
            }

            children.remove(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPathAndChildren(final CompressedPublicKey publicKey,
                                   final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            peers.add(publicKey);
            final boolean firstPath = paths.get(publicKey).isEmpty();
            paths.put(publicKey, path);

            if (firstPath) {
                eventConsumer.accept(new PeerDirectEvent(Peer.of(publicKey)));
            }

            children.add(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
