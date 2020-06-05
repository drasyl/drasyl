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
import org.drasyl.identity.Identity;

import java.net.URI;
import java.util.Collection;
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
    private final Identity identity;
    private final Set<URI> endpoints;

    PeerInformation(Identity identity, Set<URI> endpoints) {
        this.identity = identity;
        this.endpoints = endpoints;
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

    @Override
    public int hashCode() {
        return Objects.hash(identity, endpoints);
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
        return Objects.equals(identity, that.identity) &&
                Objects.equals(endpoints, that.endpoints);
    }

    @Override
    public String toString() {
        return "PeerInformation{" +
                "publicKey=" + identity +
                ", endpoints=" + endpoints +
                '}';
    }

    public Identity getIdentity() {
        try {
            lock.readLock().lock();

            return identity;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public static PeerInformation of(Identity identity, Set<URI> endpoints) {
        return new PeerInformation(identity, endpoints);
    }
}
