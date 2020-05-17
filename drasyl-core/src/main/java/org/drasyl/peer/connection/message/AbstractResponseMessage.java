package org.drasyl.peer.connection.message;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public abstract class AbstractResponseMessage<R extends RequestMessage<?>, T extends Message<?>> extends AbstractMessage<T> implements ResponseMessage<R, T> {
    protected final String correspondingId;

    protected AbstractResponseMessage() {
        correspondingId = null;
    }

    protected AbstractResponseMessage(String correspondingId) {
        this.correspondingId = requireNonNull(correspondingId);
    }

    @Override
    public String getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), correspondingId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AbstractResponseMessage<?, ?> that = (AbstractResponseMessage<?, ?>) o;
        return Objects.equals(correspondingId, that.correspondingId);
    }
}