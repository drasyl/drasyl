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

import com.google.common.collect.ImmutableSet;
import org.drasyl.core.models.CompressedPublicKey;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contains information on a specific peer (e.g. known endpoints and active connections).
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeerInformation {
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    private final Set<URI> endpoints;
    private final Set<PeerConnection> connections;
    private CompressedPublicKey publicKey;

    public PeerInformation() {
        this(new HashSet<>(), new HashSet<>(), null);
    }

    PeerInformation(Set<URI> endpoints,
                    Set<PeerConnection> connections, CompressedPublicKey publicKey) {
        this.endpoints = endpoints;
        this.connections = connections;
        this.publicKey = publicKey;
    }

    public Set<PeerConnection> getConnections() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(connections);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean addPeerConnection(PeerConnection... connections) {
        Objects.requireNonNull(connections);

        try {
            lock.writeLock().lock();

            return this.connections.addAll(Set.of(connections));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removePeerConnection(PeerConnection... connections) {
        Objects.requireNonNull(connections);

        try {
            lock.writeLock().lock();

            return this.connections.removeAll(Set.of(connections));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<URI> getEndpoints() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(endpoints);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean addEndpoint(URI... endpoints) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            return this.endpoints.addAll(Set.of(endpoints));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addEndpoint(Collection<URI> endpoints) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            return this.endpoints.addAll(endpoints);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeEndpoint(URI... endpoints) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            return this.endpoints.removeAll(Set.of(endpoints));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CompressedPublicKey getPublicKey() {
        try {
            lock.readLock().lock();

            return publicKey;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setPublicKey(CompressedPublicKey publicKey) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            this.publicKey = publicKey;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
