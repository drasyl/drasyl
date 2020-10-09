/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.PortMappingUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;

/**
 * This is a special implementation of the Server which is used for testing. It offers additional
 * interfaces for reading internal states and for injecting messages.
 */
public class TestServer extends Server {
    private final PeerChannelGroup channelGroup;
    private final TestServerChannelInitializer channelInitializer;

    public TestServer(final Identity identity,
                      final Pipeline pipeline,
                      final PeersManager peersManager,
                      final DrasylConfig config,
                      final PeerChannelGroup channelGroup,
                      final EventLoopGroup workerGroup,
                      final EventLoopGroup bossGroup,
                      final Set<Endpoint> endpoints) {
        this(identity, config, channelGroup, workerGroup, bossGroup, new TestServerChannelInitializer(new ServerEnvironment(
                config,
                identity,
                peersManager,
                pipeline,
                endpoints,
                channelGroup,
                () -> true
        )), endpoints);
    }

    TestServer(final Identity identity,
               final DrasylConfig config,
               final PeerChannelGroup channelGroup,
               final EventLoopGroup workerGroup,
               final EventLoopGroup bossGroup,
               final TestServerChannelInitializer channelInitializer,
               final Set<Endpoint> endpoints) {
        super(
                identity,
                config,
                new ServerBootstrap().group(bossGroup, workerGroup)
                        .channel(Server.getBestServerSocketChannel())
                        .childHandler(channelInitializer),
                new AtomicBoolean(),
                null,
                null,
                new HashSet<>(),
                endpoints,
                DrasylScheduler.getInstanceHeavy(),
                PortMappingUtil::expose
        );
        this.channelGroup = channelGroup;
        this.channelInitializer = channelInitializer;
    }

    public Observable<Message> receivedMessages() {
        return channelInitializer.receivedMessages();
    }

    public Observable<Message> sentMessages() {
        return channelInitializer.sentMessages();
    }

    public CompletableFuture<Void> sendMessage(final CompressedPublicKey recipient,
                                               final Message message) {
        final Future<Void> future = channelGroup.writeAndFlush(recipient, message).awaitUninterruptibly();
        return FutureUtil.toFuture(future);
    }

    public void closeClient(final CompressedPublicKey client) {
        final Channel channel = channelGroup.find(client);
        if (channel != null) {
            final ChannelFuture future = channel.close().awaitUninterruptibly();
            if (!future.isSuccess()) {
                throw new RuntimeException(future.cause());
            }
        }
        else {
            throw new RuntimeException("No client with Public Key found: " + client);
        }
    }

    public void awaitClient(final CompressedPublicKey client) {
        await().until(() -> channelGroup.find(client) != null);
    }

    public PeerChannelGroup getChannelGroup() {
        return channelGroup;
    }
}