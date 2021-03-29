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

import org.drasyl.annotation.Nullable;
import org.drasyl.identity.CompressedPublicKey;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This event signals that the node has received a message addressed to it.
 * <p>
 * This is an immutable object.
 */
public class MessageEvent implements Event {
    private final CompressedPublicKey sender;
    private final Object payload;

    /**
     * @throws NullPointerException if {@code sender} is {@code null}
     * @deprecated Use {@link #of(CompressedPublicKey, Object)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public MessageEvent(final CompressedPublicKey sender, final Object payload) {
        this.sender = requireNonNull(sender);
        this.payload = payload;
    }

    /**
     * Returns the message's sender.
     *
     * @return the message's sender
     */
    @Nullable
    public CompressedPublicKey getSender() {
        return sender;
    }

    /**
     * Returns the message's payload.
     *
     * @return the message's payload
     */
    public Object getPayload() {
        return payload;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, payload);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MessageEvent that = (MessageEvent) o;
        return Objects.equals(sender, that.sender) &&
                Objects.deepEquals(payload, that.payload);
    }

    @Override
    public String toString() {
        return "MessageEvent{" +
                "sender=" + sender +
                ", message=" + payload +
                '}';
    }

    /**
     * Creates a new {@code MessageEvent}
     *
     * @param sender  the message's sender
     * @param payload content of the message
     * @throws NullPointerException if {@code sender} is {@code null}
     */
    public static MessageEvent of(final CompressedPublicKey sender, final Object payload) {
        return new MessageEvent(sender, payload);
    }
}
