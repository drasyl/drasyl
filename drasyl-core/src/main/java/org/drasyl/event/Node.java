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
package org.drasyl.event;

import org.drasyl.annotation.NonNull;
import org.drasyl.identity.Identity;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Used by {@link Event} to describe an event related to the local Node (e.g. {@link NodeUpEvent},
 * {@link NodeOnlineEvent}).
 * <p>
 * This is an immutable object.
 */
public class Node {
    private final Identity identity;
    private final int port;
    private final int tcpFallbackPort;

    private Node(final Identity identity, final int port, final int tcpFallbackPort) {
        this.identity = requireNonNull(identity);
        if (port < 0) {
            throw new IllegalArgumentException("port must be non-negative.");
        }
        this.port = port;
        if (tcpFallbackPort < 0) {
            throw new IllegalArgumentException("tcpFallbackPort must be non-negative.");
        }
        this.tcpFallbackPort = tcpFallbackPort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, port, tcpFallbackPort);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Node node = (Node) o;
        return Objects.equals(identity, node.identity) &&
                port == node.port &&
                tcpFallbackPort == node.tcpFallbackPort;
    }

    @Override
    public String toString() {
        return "Node{" +
                "identity=" + identity +
                ", port=" + port +
                ", tcpFallbackPort=" + tcpFallbackPort +
                '}';
    }

    /**
     * Returns the node's identity.
     *
     * @return the node's identity
     */
    @NonNull
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Returns the node's server port.
     *
     * @return the node's server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the node's tcp fallback server port.
     *
     * @return the node's tcp fallback server port
     */
    public int getTcpFallbackPort() {
        return tcpFallbackPort;
    }

    /**
     * @throws NullPointerException if {@code identity} is {@code null}
     */
    public static Node of(final Identity identity) {
        return of(identity, 0);
    }

    /**
     * @throws NullPointerException     if {@code identity} is {@code null}
     * @throws IllegalArgumentException if {@code port} is negative
     */
    public static Node of(final Identity identity, final int port) {
        return new Node(identity, port, 0);
    }

    /**
     * @throws NullPointerException     if {@code identity} is {@code null}
     * @throws IllegalArgumentException if {@code port} or {@code tcpFallbackPort} is negative
     */
    public static Node of(final Identity identity, final int port, final int tcpFallbackPort) {
        return new Node(identity, port, tcpFallbackPort);
    }
}
