package org.drasyl.jtasklet.event;

import org.drasyl.channel.DrasylChannel;

import static java.util.Objects.requireNonNull;

/**
 * Connection to broker could not be established.
 */
public class ConnectionFailed extends ConnectionEvent {
    private final Throwable cause;

    public ConnectionFailed(final DrasylChannel channel, final Throwable cause) {
        super(channel);
        this.cause = requireNonNull(cause);
    }

    public Throwable cause() {
        return cause;
    }
}
