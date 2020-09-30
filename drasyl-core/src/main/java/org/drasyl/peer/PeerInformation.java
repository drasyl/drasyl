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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.util.InternPool;

import java.util.Objects;
import java.util.Set;

/**
 * Contains information on a specific peer (e.g. known endpoints).
 * <p>
 * This is an immutable object.
 */
public class PeerInformation {
    public static final InternPool<PeerInformation> POOL = new InternPool<>();
    protected final Set<Endpoint> endpoints;

    @JsonCreator
    protected PeerInformation(@JsonProperty("endpoints") final Set<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoints);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PeerInformation that = (PeerInformation) o;
        return Objects.equals(endpoints, that.endpoints);
    }

    @Override
    public String toString() {
        return "PeerInformation{" +
                "endpoints=" + endpoints +
                '}';
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public PeerInformation intern() {
        return POOL.intern(this);
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    public static PeerInformation of(final Set<Endpoint> endpoints) {
        return new PeerInformation(Set.copyOf(endpoints)).intern();
    }

    public static PeerInformation of() {
        return of(Set.of());
    }
}