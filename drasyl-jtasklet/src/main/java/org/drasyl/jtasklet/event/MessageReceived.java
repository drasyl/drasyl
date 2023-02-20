package org.drasyl.jtasklet.event;

import org.drasyl.channel.DrasylChannel;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.message.TaskletMessage;

import static java.util.Objects.requireNonNull;

public class MessageReceived<T extends TaskletMessage> implements TaskletEvent {
    private final DrasylChannel channel;
    private final T msg;

    public MessageReceived(final DrasylChannel channel, final T msg) {
        this.channel = requireNonNull(channel);
        this.msg = requireNonNull(msg);
    }

    public DrasylChannel channel() {
        return channel;
    }

    public DrasylAddress sender() {
        return (DrasylAddress) channel.remoteAddress();
    }

    public T msg() {
        return msg;
    }
}
