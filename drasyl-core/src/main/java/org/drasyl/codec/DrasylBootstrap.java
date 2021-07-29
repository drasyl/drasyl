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
package org.drasyl.codec;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.serialization.Serialization;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class DrasylBootstrap {
    private final ServerBootstrap bootstrap;
    private DrasylConfig config = DrasylConfig.of();

    public DrasylBootstrap(final Consumer<Event> eventConsumer, final Identity identity) {
        final NioEventLoopGroup parentGroup = new NioEventLoopGroup(5);
        final NioEventLoopGroup childGroup = new NioEventLoopGroup(5);
        bootstrap = new ServerBootstrap()
                .group(parentGroup, childGroup)
                .channelFactory(() -> new DrasylServerChannel(config, identity, new PeersManager(eventConsumer, identity), new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()),
                        new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound())))
                .handler(new DrasylServerChannelInitializer(eventConsumer))
                .childHandler(new DrasylChannelInitializer(eventConsumer));
    }

    public ChannelFuture bind(final Identity identity) {
        return bootstrap.bind(identity);
    }

    public DrasylBootstrap config(final DrasylConfig config) {
        this.config = requireNonNull(config);
        return this;
    }
}
