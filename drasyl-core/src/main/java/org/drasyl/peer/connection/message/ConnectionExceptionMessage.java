package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.peer.connection.message.action.MessageAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing an exception that refers to a connection. The connection should be
 * terminated after such a message. Such an exception should always be handled.
 */
public class ConnectionExceptionMessage extends AbstractMessage<ConnectionExceptionMessage> implements RequestMessage<ConnectionExceptionMessage>, UnrestrictedPassableMessage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;

    ConnectionExceptionMessage() {
        error = null;
    }

    /**
     * Creates a new exception message.
     *
     * @param error the exception type
     */
    public ConnectionExceptionMessage(Error error) {
        this.error = requireNonNull(error);
    }

    /**
     * @return the exception
     */
    public Error getError() {
        return error;
    }

    @Override
    public MessageAction<ConnectionExceptionMessage> getAction() {
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
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
        ConnectionExceptionMessage that = (ConnectionExceptionMessage) o;
        return Objects.equals(error, that.error);
    }

    @Override
    public String toString() {
        return "ConnectionExceptionMessage{" +
                "type='" + error + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }

    /**
     * Specifies the type of the {@link ConnectionExceptionMessage}.
     */
    public enum Error {
        CONNECTION_ERROR_INITIALIZATION("Error occurred during initialization stage."),
        CONNECTION_ERROR_INTERNAL("Internal Error occurred."),
        CONNECTION_ERROR_HANDSHAKE("Handshake did not take place within timeout."),
        CONNECTION_ERROR_PING_PONG("Too many Ping Messages were not answered with a Pong Message."),
        CONNECTION_ERROR_SUPER_PEER_SAME_PUBLIC_KEY("Super Peer has sent same Public Key. You can't use yourself as a Super Peer. This would cause a routing loop. This could indicate a configuration error."),
        CONNECTION_ERROR_SUPER_PEER_WRONG_PUBLIC_KEY("Super Peer has sent an unexpected Public Key. This could indicate a configuration error or man-in-the-middle attack.");
        private static final Map<String, Error> errors = new HashMap<>();

        static {
            for (Error description : values()) {
                errors.put(description.getDescription(), description);
            }
        }

        private final String description;

        Error(String description) {
            this.description = description;
        }

        /**
         * @return a human readable representation of the reason.
         */
        @JsonValue
        public String getDescription() {
            return description;
        }

        @JsonCreator
        public static Error from(String description) {
            return errors.get(description);
        }
    }
}
