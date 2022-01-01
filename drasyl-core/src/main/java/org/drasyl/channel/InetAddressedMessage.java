/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel;

import io.netty.channel.DefaultAddressedEnvelope;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * A message that wraps another message with an {@link InetSocketAddress}.
 *
 * @param <M> the type of the wrapped message
 */
public class InetAddressedMessage<M> extends DefaultAddressedEnvelope<M, InetSocketAddress> {
    /**
     * @throws NullPointerException if {@code message} or {@code recipient} is {@code null}
     */
    public InetAddressedMessage(final M message, final InetSocketAddress recipient) {
        super(message, recipient);
    }

    /**
     * @throws NullPointerException if {@code message} or {@code recipient} and {@code sender} are
     *                              {@code null}
     */
    public InetAddressedMessage(final M message,
                                final InetSocketAddress recipient,
                                final InetSocketAddress sender) {
        super(message, recipient, sender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender(), recipient(), content());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InetAddressedMessage<?> that = (InetAddressedMessage<?>) o;
        return Objects.equals(sender(), that.sender()) &&
                Objects.equals(recipient(), that.recipient()) &&
                Objects.equals(content(), that.content());
    }

    @Override
    public InetAddressedMessage<M> retain() {
        super.retain();
        return this;
    }

    @Override
    public InetAddressedMessage<M> retain(final int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public InetAddressedMessage<M> touch() {
        super.touch();
        return this;
    }

    @Override
    public InetAddressedMessage<M> touch(final Object hint) {
        super.touch(hint);
        return this;
    }

    /**
     * Returns a copy of this message with {@code newRecipient} as the new {@link #recipient()}.
     */
    public InetAddressedMessage<M> route(final InetSocketAddress newRecipient) {
        return new InetAddressedMessage<>(content(), newRecipient, sender());
    }

    /**
     * Returns a copy of this message with {@code newContent} as the new {@link #content()}.
     */
    public <N> InetAddressedMessage<N> replace(final N newContent) {
        return new InetAddressedMessage<>(newContent, recipient(), sender());
    }
}
