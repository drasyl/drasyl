package org.drasyl.jtasklet.event;

import org.drasyl.channel.DrasylChannel;

public class ConnectionEstablished extends ConnectionEvent {
    public ConnectionEstablished(final DrasylChannel channel) {
        super(channel);
    }
}
