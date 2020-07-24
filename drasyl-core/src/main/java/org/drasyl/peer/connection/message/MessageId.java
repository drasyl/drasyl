package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.Crypto;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Message} is uniquely identified by its identifier.
 */
public class MessageId {
    @JsonValue
    private final String id;

    public MessageId(String id) {
        this.id = requireNonNull(id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MessageId messageId = (MessageId) o;
        return Objects.equals(id, messageId.id);
    }

    @Override
    public String toString() {
        return id;
    }

    /**
     * Static factory to retrieve a randomly generated {@link MessageId}.
     *
     * @return A randomly generated {@code MessageId}
     */
    public static MessageId randomMessageId() {
        return new MessageId(Crypto.randomString(12));
    }
}
