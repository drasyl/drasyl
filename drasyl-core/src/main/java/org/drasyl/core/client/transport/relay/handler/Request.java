package org.drasyl.core.client.transport.relay.handler;

import org.drasyl.core.common.messages.IMessage;

import java.util.concurrent.CompletableFuture;

public class Request<T extends IMessage> {
    private final T message;
    private final CompletableFuture<IMessage> responseFuture;

    public Request(T message) {
        this.message = message;
        this.responseFuture = new CompletableFuture<>();
    }

    public T getMessage() {
        return message;
    }

    public CompletableFuture<IMessage> getResponse() {
        return responseFuture;
    }

    public boolean completeRequest(IMessage message) {
        return responseFuture.complete(message);
    }

    public boolean completed() {
        return responseFuture.isDone();
    }
}
