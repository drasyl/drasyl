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
package org.drasyl.peer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.drasyl.identity.Identity;
import org.drasyl.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the identities, public keys,
 * available interfaces, connections or relations (e.g. direct/relayed connection, super peer,
 * child, grandchild). Before a relation is set for a peer, it must be ensured that its information
 * is available. Likewise, the information may not be removed from a peer if the peer still has a
 * relation
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeersManager {
    private final ReadWriteLock lock;
    private final Map<Identity, PeerInformation> peers;
    private final Set<Identity> children;
    private Identity superPeer;

    public PeersManager() {
        this(new ReentrantReadWriteLock(true), new HashMap<>(), new HashSet<>(), null);
    }

    public PeersManager(ReadWriteLock lock, Map<Identity, PeerInformation> peers,
                        Set<Identity> children, Identity superPeer) {
        this.lock = lock;
        this.peers = peers;
        this.children = children;
        this.superPeer = superPeer;
    }

    public Map<Identity, PeerInformation> getPeers() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(peers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isPeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return peers.containsKey(identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addPeer(Identity identity,
                        PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            peers.put(identity, peerInformation);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPeers(Map<? extends Identity, ? extends PeerInformation> peers) {
        requireNonNull(peers);

        try {
            lock.writeLock().lock();

            this.peers.putAll(peers);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public PeerInformation removePeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.writeLock().lock();

            if (superPeer == identity) {
                throw new IllegalArgumentException("Peer cannot be removed. It is defined as Super Peer");
            }
            if (children.contains(identity)) {
                throw new IllegalArgumentException("Peer cannot be removed. It is defined as Children");
            }

            return peers.remove(identity);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removePeers(Identity... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            // validate
            for (Identity identity : identities) {
                if (superPeer == identity) {
                    throw new IllegalArgumentException("Peer cannot be removed. It is defined as Super Peer");
                }
                if (children.contains(identity)) {
                    throw new IllegalArgumentException("Peer cannot be removed. It is defined as Children");
                }
            }

            // remove
            for (Identity identity : identities) {
                peers.remove(identity);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public Set<Identity> getChildren() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(children);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isChildren(Identity identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return children.contains(identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean addChildren(Identity... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            // validate
            for (Identity identity : identities) {
                if (!peers.containsKey(identity)) {
                    throw new IllegalArgumentException("Peer cannot be set as a child. There are no Peer Information available");
                }
            }

            // add
            return children.addAll(List.of(identities));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeChildren(Identity... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            return children.removeAll(List.of(identities));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public PeerInformation getPeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return peers.get(identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns identity and information about Super Peer. If no Super Peer is defined, then
     * <code>null</code> is returned.
     *
     * @return
     */
    public Pair<Identity, PeerInformation> getSuperPeer() {
        try {
            lock.readLock().lock();

            if (superPeer == null) {
                return null;
            }
            else {
                return Pair.of(superPeer, peers.get(superPeer));
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void setSuperPeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.writeLock().lock();

            if (!peers.containsKey(identity)) {
                throw new IllegalArgumentException("Peer cannot be set as a Super Peer. There are no Peer Information available");
            }

            this.superPeer = identity;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSuperPeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return Objects.equals(superPeer, identity);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void unsetSuperPeer() {
        try {
            lock.writeLock().lock();

            superPeer = null;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #addPeer(Identity, PeerInformation)} and {@link
     * #setSuperPeer(Identity)}.
     */
    public void addPeerAndSetSuperPeer(Identity identity, PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            peers.put(identity, peerInformation);
            superPeer = identity;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shortcut for call {@link #addPeer(Identity, PeerInformation)} and {@link
     * #addChildren(Identity...)}.
     */
    public void addPeerAndAddChildren(Identity identity, PeerInformation peerInformation) {
        requireNonNull(identity);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();

            peers.put(identity, peerInformation);
            children.add(identity);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
