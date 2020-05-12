package org.drasyl.core.common.message;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

public abstract class AbstractResponseMessage<R extends RequestMessage, T extends Message> extends AbstractMessage<T> implements ResponseMessage<R, T> {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected final String correspondingId;

    protected AbstractResponseMessage(String correspondingId) {
        this.correspondingId = correspondingId;
    }

    @Override
    public String getCorrespondingId() {
        return correspondingId;
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

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), correspondingId);
    }
}