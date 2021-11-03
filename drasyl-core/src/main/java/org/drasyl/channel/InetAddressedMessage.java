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
     * @throws NullPointerException if {@code message} or {@code address} is {@code null}
     */
    public InetAddressedMessage(final M message, final InetSocketAddress address) {
        super(message, address, address);
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
