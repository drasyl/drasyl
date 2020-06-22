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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.superpeer.TestNodeServerChannelInitializer;

import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;

/**
 * This is a special implementation of the NodeServer which is used for testing. It offers
 * additional interfaces for reading internal states and for injecting messages.
 */
public class TestNodeServer extends NodeServer {
    public TestNodeServer(Supplier<Identity> identitySupplier,
                          Messenger messenger,
                          PeersManager peersManager,
                          DrasylConfig config,
                          EventLoopGroup workerGroup,
                          EventLoopGroup bossGroup,
                          Observable<Boolean> superPeerConnected) throws NodeServerException {
        super(identitySupplier, messenger, peersManager, config, workerGroup, bossGroup, superPeerConnected);
    }

    public Observable<Message> receivedMessages() {
        return ((TestNodeServerChannelInitializer) channelInitializer).receivedMessages();
    }

    public Observable<Message> sentMessages() {
        return ((TestNodeServerChannelInitializer) channelInitializer).sentMessages();
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
}
