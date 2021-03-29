/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
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
