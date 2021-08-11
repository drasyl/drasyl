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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.util.internal.ObjectUtil;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap that helps to bootstrap {@link DrasylChannel}s to communicate with peers.
 */
public class DrasylBootstrap {
    private volatile EventLoopGroup parentGroup; // NOSONAR
    private volatile EventLoopGroup childGroup; // NOSONAR
    private final DrasylConfig config;
    private final Identity identity;
    private final ChannelFactory<ServerChannel> channelFactory;
    private ChannelHandler handler;
    private ChannelHandler childHandler;

    /**
     * Creates a new instance which uses the given {@code DrasylConfig} and {@code Consumer<Event>}
     * to bootstrap the {@link DrasylChannel}s.
     *
     * @throws IOException if identity could not be loaded or created
     */
    public DrasylBootstrap(final DrasylConfig config,
                           final Identity identity,
                           final PeersManager peersManager) throws IOException {
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        channelFactory = () -> new DefaultDrasylServerChannel(config, identity);
        handler = new DrasylServerChannelInitializer(config, new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()), new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound()), peersManager, identity);
    }

    /**
     * Specify the {@link EventLoopGroup} which is used for the parent channel and the child
     * channels.
     */
    public DrasylBootstrap group(final EventLoopGroup group) {
        return group(group, group);
    }

    /**
     * Set the {@link EventLoopGroup} for the parent (acceptor) and the child (client). These {@link
     * EventLoopGroup}'s are used to handle all the events and IO for {@link ServerChannel} and
     * {@link Channel}'s.
     */
    public DrasylBootstrap group(final EventLoopGroup parentGroup,
                                 final EventLoopGroup childGroup) {
        if (this.parentGroup != null) {
            throw new IllegalStateException("parentGroup set already");
        }
        this.parentGroup = ObjectUtil.checkNotNull(parentGroup, "parentGroup");

        if (this.childGroup != null) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = ObjectUtil.checkNotNull(childGroup, "childGroup");

        return this;
    }

    /**
     * Set the {@link ChannelHandler} which is used to handle overlay network management and spawn
     * new child {@link Channel}'s for peer communication.
     */
    public DrasylBootstrap handler(final ChannelHandler handler) {
        this.handler = requireNonNull(handler);
        return this;
    }

    /**
     * Set the {@link ChannelHandler} which is used to handle peer communication.
     */
    public DrasylBootstrap childHandler(final ChannelHandler childHandler) {
        this.childHandler = requireNonNull(childHandler);
        return this;
    }

    /**
     * Returns the {@link DrasylConfig} used by this bootstrap.
     */
    public DrasylConfig config() {
        return config;
    }

    /**
     * Returns the {@link Identity} used by this bootstrap.
     */
    public Identity identity() {
        return identity;
    }

    /**
     * Create a new {@link Channel} and bind it to {@link #identity}.
     */
    public ChannelFuture bind() {
        return new ServerBootstrap()
                .group(parentGroup, childGroup)
                .localAddress(identity)
                .channelFactory(channelFactory)
                .handler(handler)
                .childHandler(childHandler)
                .bind();
    }
}
