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

import com.google.common.collect.ImmutableSet;
import org.drasyl.identity.CompressedPublicKey;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contains information on a specific peer (e.g. identity, public key, and known endpoints).
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeerInformation {
    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Set<URI> endpoints;
    private CompressedPublicKey publicKey;

    public PeerInformation() {
        this(new HashSet<>(), null);
    }

    PeerInformation(Set<URI> endpoints,
                    CompressedPublicKey publicKey) {
        this.endpoints = endpoints;
        this.publicKey = publicKey;
    }

    public Set<URI> getEndpoints() {
        try {
            lock.readLock().lock();

            return ImmutableSet.copyOf(endpoints);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public boolean addEndpoint(URI... endpoints) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            return this.endpoints.addAll(Set.of(endpoints));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addEndpoint(Collection<URI> endpoints) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            return this.endpoints.addAll(endpoints);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeEndpoint(URI... endpoints) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            return this.endpoints.removeAll(Set.of(endpoints));
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public CompressedPublicKey getPublicKey() {
        try {
            lock.readLock().lock();

            return publicKey;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void setPublicKey(CompressedPublicKey publicKey) {
        Objects.requireNonNull(endpoints);

        try {
            lock.writeLock().lock();

            this.publicKey = publicKey;
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoints, publicKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PeerInformation that = (PeerInformation) o;
        return Objects.equals(endpoints, that.endpoints) &&
                Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public String toString() {
        return "PeerInformation{" +
                "endpoints=" + endpoints +
                ", publicKey=" + publicKey +
                '}';
    }
}
