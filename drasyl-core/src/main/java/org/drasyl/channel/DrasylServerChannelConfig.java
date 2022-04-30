package org.drasyl.channel;

import io.netty.channel.ChannelConfig;

/**
 * A {@link ChannelConfig} for a {@link DrasylServerChannel}.
 */
public interface DrasylServerChannelConfig extends ChannelConfig {
    boolean createChannelOnPathEvent();

    DrasylServerChannelConfig setCreateChannelOnPathEvent(boolean createChannelOnPathEvent);
}
