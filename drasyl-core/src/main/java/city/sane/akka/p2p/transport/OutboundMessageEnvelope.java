package city.sane.akka.p2p.transport;

import akka.actor.ActorRef;
import city.sane.akka.p2p.P2PActorRef;

import java.util.Objects;

public class OutboundMessageEnvelope {
    private final Object message;
    private final ActorRef sender;
    private final P2PActorRef recipient;

    public OutboundMessageEnvelope(Object message, ActorRef sender, P2PActorRef recipient) {
        this.message = message;
        this.sender = sender;
        this.recipient = recipient;
    }

    public Object getMessage() {
        return message;
    }

    public ActorRef getSender() {
        return sender;
    }

    public P2PActorRef getRecipient() {
        return recipient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutboundMessageEnvelope)) {
            return false;
        }
        OutboundMessageEnvelope that = (OutboundMessageEnvelope) o;
        return Objects.equals(message, that.message) &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(recipient, that.recipient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, sender, recipient);
    }

    @Override
    public String toString() {
        return String.format("OutboundMessageEnvelope(msg=%s, sender=%s, recipient=%s)", message, sender, recipient);
    }
}
