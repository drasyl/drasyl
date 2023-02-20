package org.drasyl.jtasklet.event;

import org.drasyl.channel.DrasylChannel;
import org.drasyl.identity.DrasylAddress;

import static java.util.Objects.requireNonNull;

public abstract class ConnectionEvent implements TaskletEvent {
    protected final DrasylChannel channel;

    public ConnectionEvent(final DrasylChannel channel) {
        this.channel = requireNonNull(channel);
    }

    public DrasylChannel channel() {
        return channel;
    }

    public DrasylAddress sender() {
        return (DrasylAddress) channel.remoteAddress();
    }
}
