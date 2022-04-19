package org.drasyl.jtasklet.event;

import org.drasyl.channel.DrasylChannel;

/**
 * Connection to broker has been closed (by remote peer).
 */
public class ConnectionClosed extends ConnectionEvent {
    public ConnectionClosed(final DrasylChannel channel) {
        super(channel);
    }
}
