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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoop;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;

import java.net.SocketAddress;

import static java.util.Objects.requireNonNull;

/**
 * A virtual {@link io.netty.channel.ServerChannel} used for overlay network management. This
 * channel must be bind to an {@link Identity}.
 * <p>
 * (Currently) only compatible with {@link io.netty.channel.nio.NioEventLoop}.
 * <p>
 * Inspired by {@link io.netty.channel.local.LocalServerChannel}.
 *
 * @see DrasylChannel
 */
public class DrasylServerChannel extends AbstractServerChannel {
    enum State {OPEN, ACTIVE, CLOSED}

    private volatile State state;
    private final ChannelConfig config = new DefaultChannelConfig(this);
    private ChannelGroup channels;
    private volatile Identity localAddress; // NOSONAR

    @SuppressWarnings("java:S2384")
    DrasylServerChannel(final State state,
                        final ChannelGroup channels,
                        final Identity localAddress) {
        this.state = requireNonNull(state);
        this.channels = channels;
        this.localAddress = localAddress;
    }

    @SuppressWarnings("unused")
    public DrasylServerChannel() {
        this(State.OPEN, null, null);
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
        state = State.ACTIVE;
    }

    @Override
    protected void doRegister() throws Exception {
        super.doRegister();

        pipeline().addLast((new ChannelInitializer<>() {
            @Override
            public void initChannel(final Channel ch) {
                ch.pipeline().addLast(new ChildChannelRouter());
            }
        }));
    }

    @Override
    protected void doClose() {
        if (state != State.CLOSED) {
            // Update the internal state before the closeFuture<?> is notified.
            if (localAddress != null) {
                localAddress = null;
            }
            state = State.CLOSED;

            // close all child channels
            if (channels != null) {
                channels.close();
            }
        }
    }

    @Override
    protected void doBeginRead() {
        // NOOP
        // all inbound messages (UdpServer, UdpMulticastServer, TcpServer, TcpClient, ..) are
        // currently automatically pushed to us. no need to pull reads
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    @SuppressWarnings("java:S2384")
    public ChannelGroup channels() {
        return channels;
    }

    public synchronized Channel getOrCreateChildChannel(final IdentityPublicKey peer) {
        final ChannelHandlerContext routerCtx = pipeline().context(ChildChannelRouter.class);

        if (channels != null) {
            for (final Channel c : channels) {
                if (peer.equals(c.remoteAddress())) {
                    return c;
                }
            }
        }
        else {
            channels = new DefaultChannelGroup(routerCtx.executor());
        }

        final Channel channel = new DrasylChannel(routerCtx.channel(), peer);
        channels.add(channel);
        routerCtx.fireChannelRead(channel);

        return channel;
    }

    private static class ChildChannelRouter extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(final ChannelHandlerContext ctx,
                                final Object msg) throws Exception {
            final AddressedMessage<?, IdentityPublicKey> childMsg = (AddressedMessage<?, IdentityPublicKey>) msg;
            final Object o = childMsg.message();
            final IdentityPublicKey peer = childMsg.address();

            // create/get channel
            final Channel channel = ((DrasylServerChannel) ctx.channel()).getOrCreateChildChannel(peer);

            // pass message to channel
            channel.pipeline().fireChannelRead(o);
        }
    }
}
