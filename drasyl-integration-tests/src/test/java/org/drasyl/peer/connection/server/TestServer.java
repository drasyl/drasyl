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
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.Message;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;

/**
 * This is a special implementation of the Server which is used for testing. It offers additional
 * interfaces for reading internal states and for injecting messages.
 */
public class TestServer extends Server {
    private final TestServerChannelInitializer channelInitializer;

    public TestServer(Supplier<Identity> identitySupplier,
                      Messenger messenger,
                      PeersManager peersManager,
                      DrasylConfig config,
                      PeerChannelGroup channelGroup,
                      EventLoopGroup workerGroup,
                      EventLoopGroup bossGroup,
                      Observable<Boolean> superPeerConnected) {
        this(config, channelGroup, workerGroup, bossGroup, new TestServerChannelInitializer(new ServerEnvironment(
                config,
                identitySupplier,
                peersManager,
                messenger,
                new HashSet<>(),
                channelGroup,
                () -> true,
                () -> !config.isSuperPeerEnabled() || superPeerConnected.blockingFirst(),
                publicKey -> {
                })));
    }

    TestServer(DrasylConfig config,
               PeerChannelGroup channelGroup,
               EventLoopGroup workerGroup,
               EventLoopGroup bossGroup,
               TestServerChannelInitializer channelInitializer) {
        super(
                config,
                channelGroup,
                new AtomicBoolean(),
                new HashSet<>(),
                new ServerBootstrap().group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(channelInitializer));
        this.channelInitializer = channelInitializer;
    }

    public Observable<Message> receivedMessages() {
        return channelInitializer.receivedMessages();
    }

    public Observable<Message> sentMessages() {
        return channelInitializer.sentMessages();
    }

    public void sendMessage(CompressedPublicKey recipient, Message message) {
        ChannelFuture future = channelGroup.writeAndFlush(recipient, message).awaitUninterruptibly();
        if (!future.isSuccess()) {
            throw new RuntimeException(future.cause());
        }
    }

    public void closeClient(CompressedPublicKey client) {
        Channel channel = channelGroup.find(client);
        if (channel != null) {
            ChannelFuture future = channel.close().awaitUninterruptibly();
            if (!future.isSuccess()) {
                throw new RuntimeException(future.cause());
            }
        }
        else {
            throw new RuntimeException("No client with Public Key found: " + client);
        }
    }

    public void awaitClient(CompressedPublicKey client) {
        await().until(() -> channelGroup.find(client) != null);
    }

    public PeerChannelGroup getChannelGroup() {
        return channelGroup;
    }
}
