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
import org.drasyl.util.internal.Nullable;
import org.drasyl.identity.DrasylAddress;

import java.util.Arrays;
import java.util.Objects;

/**
 * This event signals that the node has received a message addressed to it.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class MessageEvent implements Event {
    /**
     * Returns the message's sender.
     *
     * @return the message's sender
     */
    public abstract DrasylAddress getSender();

    /**
     * Returns the message's payload.
     *
     * @return the message's payload
     */
    @Nullable
    public abstract Object getPayload();

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MessageEvent that = (MessageEvent) o;
        return Objects.equals(getSender(), that.getSender()) &&
                Objects.deepEquals(getPayload(), that.getPayload());
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(new Object[]{ getSender(), getPayload() });
    }

    /**
     * Creates a new {@code MessageEvent}
     *
     * @param sender  the message's sender
     * @param payload content of the message
     * @throws NullPointerException if {@code sender} is {@code null}
     */
    public static MessageEvent of(final DrasylAddress sender, final Object payload) {
        return new AutoValue_MessageEvent(sender, payload);
    }
}
