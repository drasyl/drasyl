package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.Message;

public abstract class AbstractMessageAction<T extends Message<?>> implements MessageAction<T> {
    protected final T message;

    public AbstractMessageAction(T message) {
        this.message = message;
    }
}
