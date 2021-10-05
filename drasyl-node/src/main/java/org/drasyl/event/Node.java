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

import com.google.auto.value.AutoValue;
import org.drasyl.identity.Identity;

import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * Used by {@link Event} to describe an event related to the local Node (e.g. {@link NodeUpEvent},
 * {@link NodeOnlineEvent}).
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class Node {
    /**
     * Returns the node's identity.
     *
     * @return the node's identity
     */
    public abstract Identity getIdentity();

    /**
     * Returns the node's server port.
     *
     * @return the node's server port
     */
    public abstract int getPort();

    /**
     * Returns the node's tcp fallback server port.
     *
     * @return the node's tcp fallback server port
     */
    public abstract int getTcpFallbackPort();

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
        return of(identity, port, 0);
    }

    /**
     * @throws NullPointerException     if {@code identity} is {@code null}
     * @throws IllegalArgumentException if {@code port} or {@code tcpFallbackPort} is negative
     */
    public static Node of(final Identity identity, final int port, final int tcpFallbackPort) {
        return new AutoValue_Node(
                identity,
                requireNonNegative(port, "port must be non-negative"),
                requireNonNegative(tcpFallbackPort, "tcpFallbackPort must be non-negative")
        );
    }
}
