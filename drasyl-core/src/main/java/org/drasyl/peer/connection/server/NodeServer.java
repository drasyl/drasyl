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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.QuitMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.util.UriUtil.overridePort;

@SuppressWarnings({ "squid:S00107" })
public class NodeServer implements AutoCloseable {
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    public final ServerBootstrap serverBootstrap;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final DrasylNodeConfig config;
    private final Messenger messenger;
    private final AtomicBoolean opened;
    private final NodeServerChannelGroup channelGroup;
    private final Observable<Boolean> superPeerConnected;
    private Channel channel;
    private NodeServerChannelBootstrap channelBootstrap;
    private int actualPort;
    private Set<URI> actualEndpoints;

    /**
     * Starts a node server for forwarding messages to child peers.<br> Default Port: 22527
     *
     * @param identityManager    the identity manager
     * @param messenger          the messenger object
     * @param peersManager       the peers manager
     * @param workerGroup        netty shared worker group
     * @param bossGroup          netty shared boss group
     * @param superPeerConnected
     * @throws DrasylException if the loaded default config is invalid
     */
    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      EventLoopGroup workerGroup,
                      EventLoopGroup bossGroup,
                      Observable<Boolean> superPeerConnected) throws DrasylException {
        this(identityManager, messenger, peersManager, superPeerConnected, ConfigFactory.load(), workerGroup, bossGroup);
    }

    /**
     * Node server for forwarding messages to child peers.
     *
     * @param identityManager    the identity manager
     * @param messenger          the messenger object
     * @param peersManager       the peers manager
     * @param superPeerConnected
     * @param config             config that should be used
     * @param workerGroup        netty shared worker group
     * @param bossGroup          netty shared boss group
     * @throws DrasylException if the given config is invalid
     */
    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      Observable<Boolean> superPeerConnected,
                      Config config,
                      EventLoopGroup workerGroup,
                      EventLoopGroup bossGroup) throws DrasylException {
        this(identityManager, messenger, peersManager, superPeerConnected, new DrasylNodeConfig(config), workerGroup, bossGroup);
    }

    /**
     * Node server for forwarding messages to child peers.
     *  @param identityManager the identity manager
     * @param messenger       the messenger object
     * @param peersManager    the peers manager
     * @param superPeerConnected
     * @param config          config that should be used
     * @param workerGroup     netty shared worker group
     * @param bossGroup       netty shared boss group
     */
    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      Observable<Boolean> superPeerConnected,
                      DrasylNodeConfig config,
                      EventLoopGroup workerGroup,
                      EventLoopGroup bossGroup) throws NodeServerException {
        this(identityManager,
                messenger,
                peersManager,
                config,
                null,
                new ServerBootstrap(),
                workerGroup,
                bossGroup,
                null,
                new AtomicBoolean(false),
                -1,
                new HashSet<>(),
                new NodeServerChannelGroup(),
                superPeerConnected);

        channelBootstrap = new NodeServerChannelBootstrap(config, this, serverBootstrap);
    }

    NodeServer(IdentityManager identityManager,
               Messenger messenger,
               PeersManager peersManager,
               DrasylNodeConfig config,
               Channel channel,
               ServerBootstrap serverBootstrap,
               EventLoopGroup workerGroup,
               EventLoopGroup bossGroup,
               NodeServerChannelBootstrap channelBootstrap,
               AtomicBoolean opened,
               int actualPort,
               Set<URI> actualEndpoints,
               NodeServerChannelGroup channelGroup,
               Observable<Boolean> superPeerConnected) {
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.config = config;
        this.channel = channel;
        this.serverBootstrap = serverBootstrap;
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.channelBootstrap = channelBootstrap;
        this.opened = opened;
        this.messenger = messenger;
        this.actualPort = actualPort;
        this.actualEndpoints = actualEndpoints;
        this.channelGroup = channelGroup;
        this.superPeerConnected = superPeerConnected;
    }

    NodeServerChannelGroup getChannelGroup() {
        return channelGroup;
    }

    Messenger getMessenger() {
        return messenger;
    }

    /**
     * @return the peers manager
     */
    PeersManager getPeersManager() {
        return peersManager;
    }

    /**
     * @return the endpoints
     */
    public Set<URI> getEndpoints() {
        return actualEndpoints;
    }

    EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public boolean isOpen() {
        return opened.get();
    }

    IdentityManager getIdentityManager() {
        return identityManager;
    }

    Observable<Boolean> getSuperPeerConnected() {
        return superPeerConnected;
    }

    /**
     * Starts the relay server.
     */
    public void open() throws NodeServerException {
        if (opened.compareAndSet(false, true)) {
            ChannelFuture channelFuture = channelBootstrap.getChannel();
            channelFuture.awaitUninterruptibly();

            if (channelFuture.isSuccess()) {
                channel = channelFuture.channel();

                channel.closeFuture().addListener(future -> {
                    actualPort = -1;
                    actualEndpoints = Set.of();
                    messenger.unsetServerSink();
                });

                InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
                actualPort = socketAddress.getPort();
                actualEndpoints = config.getServerEndpoints().stream()
                        .map(uri -> {
                            if (uri.getPort() == 0) {
                                return overridePort(uri, actualPort);
                            }
                            return uri;
                        }).collect(Collectors.toSet());
                messenger.setServerSink((recipient, message) -> {
                    // if recipient is a grandchild, we must send message to appropriate child
                    CompressedPublicKey grandchildrenPath = peersManager.getGrandchildrenRoutes().get(recipient);
                    if (grandchildrenPath != null) {
                        recipient = grandchildrenPath;
                    }

                    try {
                        channelGroup.writeAndFlush(recipient, message);
                    }
                    catch (IllegalArgumentException e) {
                        throw new NoPathToIdentityException(recipient);
                    }
                });
            }
            else {
                throw new NodeServerException("Unable to start server: " + channelFuture.cause().getMessage());
            }
        }
    }

    /**
     * Returns the actual bind port used by this server.
     */
    public int getPort() {
        return actualPort;
    }

    /**
     * Closes the server socket and all open client sockets.
     */
    @Override
    @SuppressWarnings({ "java:S1905" })
    public void close() {
        if (opened.compareAndSet(true, false) && channel != null && channel.isOpen()) {
            // send quit message to all clients and close connections
            channelGroup.writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN))
                    .addListener((ChannelGroupFutureListener) future -> {
                        future.group().close();

                        // shutdown server
                        channel.close();
                        channel = null;
                    });
        }
    }
}
