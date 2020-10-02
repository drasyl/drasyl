package org.drasyl.event;

import org.drasyl.identity.CompressedPublicKey;

import java.util.Objects;

/**
 * This event signals that the node has received a message addressed to it.
 * <p>
 * This is an immutable object.
 */
public class MessageEvent implements Event {
    private final CompressedPublicKey sender;
    private final Object payload;

    public MessageEvent(final CompressedPublicKey sender, final Object payload) {
        this.sender = sender;
        this.payload = payload;
    }

    /**
     * @return the message's sender
     */
    public CompressedPublicKey getSender() {
        return sender;
    }

    /**
     * @return th message
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
                Objects.equals(payload, that.payload);
    }

    @Override
    public String toString() {
        return "MessageEvent{" +
                "sender=" + sender +
                ", message=" + payload +
                '}';
    }
}