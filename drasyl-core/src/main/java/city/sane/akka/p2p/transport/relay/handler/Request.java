package city.sane.akka.p2p.transport.relay.handler;

import org.drasyl.core.common.messages.Message;

import java.util.concurrent.CompletableFuture;

public class Request<T extends Message> {
    private final T message;
    private final CompletableFuture<Message> responseFuture;

    public Request(T message) {
        this.message = message;
        this.responseFuture = new CompletableFuture<>();
    }

    public T getMessage() {
        return message;
    }

    public CompletableFuture<Message> getResponse() {
        return responseFuture;
    }

    public boolean completeRequest(Message message) {
        return responseFuture.complete(message);
    }

    public boolean completed() {
        return responseFuture.isDone();
    }
}
