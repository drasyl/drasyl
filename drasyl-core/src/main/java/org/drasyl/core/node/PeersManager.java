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
package org.drasyl.core.node;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.drasyl.core.node.identity.Identity;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the identities, public keys,
 * available interfaces, connections or relations (e.g. direct/relayed connection, super peer,
 * child, grandchild).
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeersManager {
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Map<Identity, PeerInformation> peers;
    private final Set<Identity> children;
    private Identity superPeer;

    public PeersManager() {
        this(new HashMap<>(), null, new HashSet<>());
    }

    PeersManager(Map<Identity, PeerInformation> peers,
                 Identity superPeer,
                 Set<Identity> children) {
        this.peers = peers;
        this.superPeer = superPeer;
        this.children = children;
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

            for (int i = 0; i < identities.length; i++) {
                peers.remove(identities[i]);
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

    public boolean addChildren(Identity identity) {
        requireNonNull(identity);

        try {
            lock.writeLock().lock();

            return children.add(identity);
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

    public PeerInformation getSuperPeerInformation() {
        try {
            lock.readLock().lock();

            return getPeer(getSuperPeer());
        }
        finally {
            lock.readLock().unlock();
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

    public Identity getSuperPeer() {
        try {
            lock.readLock().lock();

            return superPeer;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void setSuperPeer(Identity superPeer) {
        requireNonNull(superPeer);

        try {
            lock.writeLock().lock();

            this.superPeer = superPeer;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isSuperPeer(Identity identity) {
        requireNonNull(identity);

        try {
            lock.readLock().lock();

            return Objects.equals(getSuperPeer(), identity);
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
