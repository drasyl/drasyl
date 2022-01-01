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
import org.drasyl.identity.DrasylAddress;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * A message that wraps another message with a {@link DrasylAddress}.
 *
 * @param <M> the type of the wrapped message
 */
public class OverlayAddressedMessage<M> extends DefaultAddressedEnvelope<M, DrasylAddress> {
    /**
     * @throws NullPointerException if {@code message} or {@code recipient} is {@code null}
     */
    public OverlayAddressedMessage(final M message, final DrasylAddress recipient) {
        super(message, recipient);
    }

    /**
     * @throws NullPointerException if {@code message} or {@code recipient} and {@code sender} are
     *                              {@code null}
     */
    public OverlayAddressedMessage(final M message,
                                   final DrasylAddress recipient,
                                   final DrasylAddress sender) {
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
        final OverlayAddressedMessage<?> that = (OverlayAddressedMessage<?>) o;
        return Objects.equals(sender(), that.sender()) &&
                Objects.equals(recipient(), that.recipient()) &&
                Objects.equals(content(), that.content());
    }

    @Override
    public OverlayAddressedMessage<M> retain() {
        super.retain();
        return this;
    }

    @Override
    public OverlayAddressedMessage<M> retain(final int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public OverlayAddressedMessage<M> touch() {
        super.touch();
        return this;
    }

    @Override
    public OverlayAddressedMessage<M> touch(final Object hint) {
        super.touch(hint);
        return this;
    }

    /**
     * Returns a copy of this message with {@code newRecipient} as the new {@link #recipient()}.
     */
    public InetAddressedMessage<M> resolve(final InetSocketAddress address) {
        return new InetAddressedMessage<>(content(), address);
    }

    /**
     * Returns a copy of this message with {@code newContent} as the new {@link #content()}.
     */
    public <N> OverlayAddressedMessage<N> replace(final N newContent) {
        return new OverlayAddressedMessage<>(newContent, recipient(), sender());
    }
}
