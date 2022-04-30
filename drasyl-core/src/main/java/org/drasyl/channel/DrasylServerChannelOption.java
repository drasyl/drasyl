package org.drasyl.channel;

import io.netty.channel.ChannelOption;

/**
 * Provides {@link ChannelOption}s for {@link DrasylServerChannel}s.
 */
public final class DrasylServerChannelOption<T> extends ChannelOption<T> {
    /**
     * Defines MTU for the created tun device (not supported on windows).
     */
    public static final ChannelOption<Boolean> CREATE_CHANNEL_ON_PATH_EVENT = valueOf("CREATE_CHANNEL_ON_PATH_EVENT");

    @SuppressWarnings({ "java:S1144", "java:S1874" })
    private DrasylServerChannelOption(final String name) {
        super(name);
    }
}
