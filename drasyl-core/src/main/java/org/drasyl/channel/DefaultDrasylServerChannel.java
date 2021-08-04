/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;
import org.drasyl.DrasylAddress;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.serialization.Serialization;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Channel} for overlay network management.
 */
public class DefaultDrasylServerChannel extends AbstractServerChannel implements DrasylServerChannel {
    private final DrasylConfig drasylConfig;
    private volatile int state; // 0 - open (node created), 1 - active (node started), 2 - closed (node shut down)
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final Identity identity;
    private final PeersManager peersManager;
    private volatile Identity localAddress; // NOSONAR
    private final Serialization inboundSerialization;
    private final Serialization outboundSerialization;
    private final Map<DrasylAddress, Channel> channels = new ConcurrentHashMap<>();

    public DefaultDrasylServerChannel(final DrasylConfig drasylConfig,
                                      final Identity identity,
                                      final PeersManager peersManager,
                                      final Serialization inboundSerialization,
                                      final Serialization outboundSerialization) {
        this.drasylConfig = requireNonNull(drasylConfig);
        this.identity = requireNonNull(identity);
        this.peersManager = requireNonNull(peersManager);
        this.inboundSerialization = requireNonNull(inboundSerialization);
        this.outboundSerialization = requireNonNull(outboundSerialization);
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    @Override
    protected Identity localAddress0() {
        return localAddress;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) {
        if (!(localAddress instanceof Identity)) {
            throw new IllegalArgumentException("Unsupported address type!");
        }

        this.localAddress = (Identity) localAddress;
        state = 1;
    }

    @Override
    protected void doClose() {
        if (state <= 1) {
            // Update all internal state before the closeFuture is notified.
            if (localAddress != null) {
                localAddress = null;
            }
            state = 2; // NOSONAR

            // close all child channels
            channels.forEach((address, channel) -> channel.close());
        }
    }

    @Override
    protected void doBeginRead() {
        // do nothing.
        // UdpServer, UdpMulticastServer, TcpServer are currently pushing their readings to us
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state < 2; // NOSONAR
    }

    @Override
    public boolean isActive() {
        return state == 1;
    }

    @Override
    public DrasylConfig drasylConfig() {
        return drasylConfig;
    }

    @Override
    public PeersManager peersManager() {
        return peersManager;
    }

    @Override
    public Serialization inboundSerialization() {
        return inboundSerialization;
    }

    @Override
    public Serialization outboundSerialization() {
        return outboundSerialization;
    }

    @Override
    public Identity identity() {
        return identity;
    }

    @Override
    public Map<DrasylAddress, Channel> channels() {
        return Map.copyOf(channels);
    }

    @Override
    public Channel getOrCreateChildChannel(final ChannelHandlerContext ctx,
                                           final IdentityPublicKey peer) {
        return channels.computeIfAbsent(peer, key -> {
            final DrasylChannel channel = new DrasylChannel(ctx.channel(), peer);
            channel.closeFuture().addListener(future -> channels.remove(key));
            ctx.fireChannelRead(channel);
            return channel;
        });
    }
}
