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
import org.drasyl.identity.Address;
import org.drasyl.util.Pair;

import java.util.*;
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
    private final Map<Address, PeerInformation> peers;
    private final Set<Address> children;
    private Address superPeer;

    public PeersManager() {
        this(new ReentrantReadWriteLock(true), new HashMap<>(), new HashSet<>(), null);
    }

    public PeersManager(ReadWriteLock lock, Map<Address, PeerInformation> peers,
                        Set<Address> children, Address superPeer) {
        this.lock = lock;
        this.peers = peers;
        this.children = children;
        this.superPeer = superPeer;
    }

    public Map<Address, PeerInformation> getPeers() {
        try {
            lock.readLock().lock();

            return ImmutableMap.copyOf(peers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isPeer(Address address) {
        requireNonNull(address);

        try {
            lock.readLock().lock();

            return peers.containsKey(address);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addPeer(Address address,
                        PeerInformation peerInformation) {
        requireNonNull(address);
        requireNonNull(peerInformation);

        try {
            lock.writeLock().lock();
            peers.put(address, peerInformation);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPeers(Map<? extends Address, ? extends PeerInformation> peers) {
        requireNonNull(peers);

        try {
            lock.writeLock().lock();

            this.peers.putAll(peers);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public PeerInformation removePeer(Address address) {
        requireNonNull(address);

        try {
            lock.writeLock().lock();

            if (superPeer == address) {
                throw new IllegalArgumentException("Peer cannot be removed. It is defined as Super Peer");
            }
            if (children.contains(address)) {
                throw new IllegalArgumentException("Peer cannot be removed. It is defined as Children");
            }

            return peers.remove(address);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removePeers(Address... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            // validate
            for (Address address : identities) {
                if (superPeer == address) {
                    throw new IllegalArgumentException("Peer cannot be removed. It is defined as Super Peer");
                }
                if (children.contains(address)) {
                    throw new IllegalArgumentException("Peer cannot be removed. It is defined as Children");
                }
            }

            // remove
            for (Address address : identities) {
                peers.remove(address);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public Set<Address> getChildren() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(children);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean isChildren(Address address) {
        requireNonNull(address);

        try {
            lock.readLock().lock();

            return children.contains(address);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean addChildren(Address... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            // validate
            for (Address address : identities) {
                if (!peers.containsKey(address)) {
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

    public boolean removeChildren(Address... identities) {
        requireNonNull(identities);

        try {
            lock.writeLock().lock();

            return children.removeAll(List.of(identities));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public PeerInformation getPeer(Address address) {
        requireNonNull(address);

        try {
            lock.readLock().lock();

            return peers.get(address);
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
    public Pair<Address, PeerInformation> getSuperPeer() {
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

    public void setSuperPeer(Address address) {
        requireNonNull(address);

        try {
            lock.writeLock().lock();

            if (!peers.containsKey(address)) {
                throw new IllegalArgumentException("Peer cannot be set as a Super Peer. There are no Peer Information available");
            }

            this.superPeer = address;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSuperPeer(Address address) {
        requireNonNull(address);

        try {
            lock.readLock().lock();

            return Objects.equals(superPeer, address);
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
}
