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
package org.drasyl.event;

import org.drasyl.identity.PrivateIdentity;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Used by {@link Event} to describe an event related to the local Node (e.g. {@link
 * EventType#EVENT_NODE_UP}, {@link EventType#EVENT_NODE_ONLINE}, {@link
 * EventType#EVENT_NODE_IDENTITY_COLLISION}).
 */
public class Node {
    private final PrivateIdentity identity;
    private final Set<URI> endpoints;

    Node(PrivateIdentity identity, Set<URI> endpoints) {
        this.identity = identity;
        this.endpoints = endpoints;
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
        Node node = (Node) o;
        return Objects.equals(identity, node.identity) &&
                Objects.equals(endpoints, node.endpoints);
    }

    @Override
    public String toString() {
        return "Node{" +
                "identity=" + identity +
                ", endpoints=" + endpoints +
                '}';
    }

    public PrivateIdentity getIdentity() {
        return identity;
    }

    public Set<URI> getEndpoints() {
        return endpoints;
    }

    public static Node of(PrivateIdentity identity) {
        return of(identity, Set.of());
    }

    public static Node of(PrivateIdentity identity, Set<URI> endpoints) {
        return new Node(identity, endpoints);
    }
}
