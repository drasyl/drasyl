package org.drasyl.core.common.message;

import org.drasyl.core.common.message.action.ConnectionExceptionMessageAction;
import org.drasyl.core.common.message.action.MessageAction;

import java.util.Objects;

/**
 * A message representing an exception that refers to a connection. The connection should be
 * terminated after such a message. Such an exception should always be handled.
 */
public class ConnectionExceptionMessage extends AbstractMessage<ConnectionExceptionMessage> implements UnrestrictedPassableMessage {
    private final String exception;

    protected ConnectionExceptionMessage() {
        exception = null;
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception
     */
    public ConnectionExceptionMessage(Exception exception) {
        this(exception.getMessage());
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception as String
     */
    public ConnectionExceptionMessage(String exception) {
        this.exception = Objects.requireNonNull(exception);
    }

    /**
     * Creates a new exception message.
     *
     * @param exception the exception
     */
    public ConnectionExceptionMessage(Throwable exception) {
        this(exception.getMessage());
    }

    /**
     * @return the exception
     */
    public String getException() {
        return exception;
    }

    @Override
    public MessageAction<ConnectionExceptionMessage> getAction() {
        return new ConnectionExceptionMessageAction(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), exception);
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
        return Objects.equals(exception, that.exception);
    }

    @Override
    public String toString() {
        return "ConnectionExceptionMessage{" +
                "exception='" + exception + '\'' +
                ", id='" + id + '\'' +
                ", signature=" + signature +
                '}';
    }
}
