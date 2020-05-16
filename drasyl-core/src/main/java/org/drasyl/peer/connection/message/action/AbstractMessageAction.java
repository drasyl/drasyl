package org.drasyl.peer.connection.message.action;

import org.drasyl.peer.connection.message.Message;

public abstract class AbstractMessageAction<T extends Message<?>> implements MessageAction<T> {
    protected final T message;

    public AbstractMessageAction(T message) {
        this.message = message;
    }
}
