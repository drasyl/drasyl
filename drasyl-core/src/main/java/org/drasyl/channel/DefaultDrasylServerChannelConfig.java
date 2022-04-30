package org.drasyl.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;

import java.util.Map;

import static org.drasyl.channel.DrasylServerChannelOption.CREATE_CHANNEL_ON_PATH_EVENT;

public class DefaultDrasylServerChannelConfig extends DefaultChannelConfig implements DrasylServerChannelConfig {
    private boolean createChannelOnPathEvent;

    public DefaultDrasylServerChannelConfig(final Channel channel) {
        super(channel);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(final ChannelOption<T> option) {
        if (option == CREATE_CHANNEL_ON_PATH_EVENT) {
            return (T) Boolean.valueOf(createChannelOnPathEvent());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(final ChannelOption<T> option, final T value) {
        if (!super.setOption(option, value)) {
            if (option == CREATE_CHANNEL_ON_PATH_EVENT) {
                setCreateChannelOnPathEvent((Boolean) value);
            }
            else {
                return false;
            }
        }

        return true;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), CREATE_CHANNEL_ON_PATH_EVENT);
    }

    @Override
    public boolean createChannelOnPathEvent() {
        return createChannelOnPathEvent;
    }

    @Override
    public DrasylServerChannelConfig setCreateChannelOnPathEvent(final boolean createChannelOnPathEvent) {
        this.createChannelOnPathEvent = createChannelOnPathEvent;
        return this;
    }
}
