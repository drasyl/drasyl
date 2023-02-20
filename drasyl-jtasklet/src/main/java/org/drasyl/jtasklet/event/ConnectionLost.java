package org.drasyl.jtasklet.event;

import org.drasyl.channel.DrasylChannel;

/**
 * Established connection to broker lost.
 */
public class ConnectionLost extends ConnectionEvent {
    public ConnectionLost(final DrasylChannel channel) {
        super(channel);
    }
}
