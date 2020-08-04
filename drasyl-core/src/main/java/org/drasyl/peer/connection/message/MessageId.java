package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.crypto.Crypto;

import java.util.Objects;

/**
 * A {@link Message} is uniquely identified by its 24 lower-case hex digit identifier.
 */
public class MessageId {
    @JsonValue
    private final String id;

    @JsonCreator
    public MessageId(String id) {
        if (!isValidMessageId(id)) {
            throw new IllegalArgumentException("ID must be a 24 lower-case hex digit string: " + id);
        }
        this.id = id;
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

    /**
     * Checks if {@code id} is a valid identifier.
     *
     * @param id string to be validated
     * @return {@code true} if valid. Otherwise {@code false}
     */
    public static boolean isValidMessageId(CharSequence id) {
        if (id == null) {
            return false;
        }

        if (id.length() != 24) {
            return false;
        }

        for (int i = 0; i < id.length(); i++) {
            switch (id.charAt(i)) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                    break;
                default:
                    return false;
            }
        }

        return true;
    }
}