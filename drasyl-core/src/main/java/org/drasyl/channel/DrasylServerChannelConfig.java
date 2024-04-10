/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import org.drasyl.handler.remote.PeersManager;

import java.util.Map;

import static io.netty.channel.ChannelOption.valueOf;

/**
 * The {@link ChannelConfig} for {@link DrasylServerChannel}s.
 */
public class DrasylServerChannelConfig extends DefaultChannelConfig {
    public static final ChannelOption<Integer> NETWORK_ID = valueOf("NETWORK_ID");
    public static final ChannelOption<PeersManager> PEERS_MANAGER = valueOf("PEERS_MANAGER");

    private volatile int networkId;
    private volatile PeersManager peersManager = new PeersManager();

    public DrasylServerChannelConfig(final Channel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), NETWORK_ID, PEERS_MANAGER);
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public <T> T getOption(ChannelOption<T> option) {
        if (option == NETWORK_ID) {
            return (T) Integer.valueOf(networkId);
        }
        if (option == PEERS_MANAGER) {
            return (T) getPeersManager();
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == NETWORK_ID) {
            setNetworkId((Integer) value);
        }
        else if (option == PEERS_MANAGER) {
            setPeersManager((PeersManager) value);
        }
        else {
            return super.setOption(option, value);
        }

        return true;
    }

    private void setNetworkId(final int networkId) {
        if (channel.isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.networkId = networkId;
    }

    private PeersManager getPeersManager() {
        return peersManager;
    }

    private void setPeersManager(final PeersManager peersManager) {
        if (channel.isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.peersManager = peersManager;
    }
}
