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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Contains information on a specific peer (e.g. identity, public key, and known endpoints).
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeerInformation {
    private final Set<URI> endpoints;
    @JsonIgnore
    private final Set<Path> paths;

    private PeerInformation() {
        endpoints = Set.of();
        paths = Set.of();
    }

    PeerInformation(Set<URI> endpoints, Set<Path> paths) {
        this.endpoints = endpoints;
        this.paths = paths;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoints, paths);
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
                Objects.equals(paths, that.paths);
    }

    public Set<URI> getEndpoints() {
        return ImmutableSet.copyOf(endpoints);
    }

    @Override
    public String toString() {
        return "PeerInformation{" +
                "endpoints=" + endpoints +
                ", paths=" + paths +
                '}';
    }

    public void add(PeerInformation other) {
        endpoints.addAll(other.getEndpoints());
        paths.addAll(other.getPaths());
    }

    public Set<Path> getPaths() {
        return paths;
    }

    public void remove(PeerInformation other) {
        endpoints.removeAll(other.getEndpoints());
        paths.removeAll(other.getPaths());
    }

    public static PeerInformation of(Set<URI> endpoints) {
        return of(endpoints, Set.of());
    }

    public static PeerInformation of(Set<URI> endpoints, Set<Path> paths) {
        return new PeerInformation(endpoints, paths);
    }

    public static PeerInformation of(Set<URI> endpoints, Path path) {
        return of(endpoints, new HashSet<>(Set.of(path)));
    }

    public static PeerInformation of() {
        return of(new HashSet<>(), new HashSet<>());
    }

    public static PeerInformation of(Path path) {
        return of(new HashSet<>(), path);
    }
}
