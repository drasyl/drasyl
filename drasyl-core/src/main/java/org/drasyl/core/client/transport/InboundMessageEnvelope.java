package org.drasyl.core.client.transport;

import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.InternalActorRef;

public class InboundMessageEnvelope {
    private final Object deserializedMessage;
    private final ActorRef akkaSender;
    private final InternalActorRef akkaRecipient;
    private final Address akkaRecipientAddress;

    public InboundMessageEnvelope(Object deserializedMessage, ActorRef akkaSender, InternalActorRef akkaRecipient, Address akkaRecipientAddress) {
        this.deserializedMessage = deserializedMessage;
        this.akkaSender = akkaSender;
        this.akkaRecipient = akkaRecipient;
        this.akkaRecipientAddress = akkaRecipientAddress;
    }

    public Object getDeserializedMessage() {
        return deserializedMessage;
    }

    public ActorRef getAkkaSender() {
        return akkaSender;
    }

    public InternalActorRef getAkkaRecipient() {
        return akkaRecipient;
    }

    public Address getAkkaRecipientAddress() {
        return akkaRecipientAddress;
    }

    @Override
    public String toString() {
        return String.format("InboundMessageEnvelope(msg=%s, sender=%s, recipient=%s)", deserializedMessage, akkaSender, akkaRecipientAddress);
    }
}
