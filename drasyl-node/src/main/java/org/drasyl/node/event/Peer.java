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
package org.drasyl.node.event;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.DrasylAddress;

/**
 * Used by {@link Event} to describe an event related to a Peer (e.g. {@link PeerRelayEvent}, {@link
 * PeerDirectEvent}).
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class Peer {
    /**
     * Returns the peer's address.
     *
     * @return the peer's address.
     */
    public abstract DrasylAddress getAddress();

    /**
     * Returns the peer's public key.
     *
     * @return the peer's public key.
     * @deprecated Use {@link #getAddress()} instead.
     */
    @Deprecated(since = "0.6.0", forRemoval = true)
    public DrasylAddress getIdentityPublicKey() {
        return getAddress();
    }

    /**
     * @throws NullPointerException if {@code address} is {@code null}
     */
    public static Peer of(final DrasylAddress address) {
        return new AutoValue_Peer(address);
    }
}
