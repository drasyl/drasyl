package org.drasyl.node;

import java.util.Arrays;
import java.util.Objects;

class Message {
    private final Object recipient;
    private final byte[] payload;

    public Message(Object recipient, byte[] payload) {
        this.recipient = recipient;
        this.payload = payload;
    }

    public Object getRecipient() {
        return recipient;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(recipient);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Message message = (Message) o;
        return Objects.equals(recipient, message.recipient) &&
                Arrays.equals(payload, message.payload);
    }

    @Override
    public String toString() {
        return "Message{" +
                "recipient=" + recipient +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }
}
