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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.drasyl.util.NetworkUtil.isMatchAllAddress;
import static org.drasyl.util.UriUtil.overridePort;

/**
 * The server binds to a defined port and thus allows the node to be discovered and contacted by
 * other peers.
 */
@SuppressWarnings({ "squid:S00107" })
public class Server implements DrasylNodeComponent {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    protected final PeerChannelGroup channelGroup;
    private final ServerBootstrap serverBootstrap;
    private final DrasylConfig config;
    private final AtomicBoolean opened;
    private final Set<URI> nodeEndpoints;
    private Channel channel;
    private int actualPort;
    private Set<URI> actualEndpoints;

    Server(DrasylConfig config,
           ServerBootstrap serverBootstrap,
           AtomicBoolean opened,
           PeerChannelGroup channelGroup,
           int actualPort, Channel channel,
           Set<URI> actualEndpoints,
           Set<URI> nodeEndpoints) {
        this.config = config;
        this.channel = channel;
        this.serverBootstrap = serverBootstrap;
        this.opened = opened;
        this.actualPort = actualPort;
        this.actualEndpoints = actualEndpoints;
        this.channelGroup = channelGroup;
        this.nodeEndpoints = nodeEndpoints;
    }

    public Server(Identity identity,
                  Messenger messenger,
                  PeersManager peersManager,
                  DrasylConfig config,
                  PeerChannelGroup channelGroup,
                  EventLoopGroup workerGroup,
                  EventLoopGroup bossGroup,
                  Observable<Boolean> superPeerConnected,
                  Consumer<CompressedPublicKey> peerCommunicationConsumer,
                  Set<URI> nodeEndpoints,
                  BooleanSupplier acceptNewConnectionsSupplier) throws ServerException {
        this(identity, messenger, peersManager, config, channelGroup, workerGroup, bossGroup, superPeerConnected, new AtomicBoolean(false), acceptNewConnectionsSupplier, peerCommunicationConsumer, nodeEndpoints);
    }

    /**
     * Server for accepting connections from child peers and non-child peers.
     *
     * @param identity                     the identity manager
     * @param messenger                    the messenger object
     * @param peersManager                 the peers manager
     * @param config                       config that should be used
     * @param channelGroup
     * @param workerGroup                  netty shared worker group
     * @param bossGroup                    netty shared boss group
     * @param superPeerConnected
     * @param acceptNewConnectionsSupplier
     * @param peerCommunicationConsumer
     * @param nodeEndpoints
     */
    public Server(Identity identity,
                  Messenger messenger,
                  PeersManager peersManager,
                  DrasylConfig config,
                  PeerChannelGroup channelGroup,
                  EventLoopGroup workerGroup,
                  EventLoopGroup bossGroup,
                  Observable<Boolean> superPeerConnected,
                  AtomicBoolean opened,
                  BooleanSupplier acceptNewConnectionsSupplier,
                  Consumer<CompressedPublicKey> peerCommunicationConsumer,
                  Set<URI> nodeEndpoints) throws ServerException {
        this(
                config,
                channelGroup,
                opened,
                nodeEndpoints,
                new ServerBootstrap().group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(initiateChannelInitializer(new ServerEnvironment(
                                        config,
                                        identity,
                                        peersManager,
                                        messenger,
                                        nodeEndpoints,
                                        channelGroup,
                                        acceptNewConnectionsSupplier,
                                        () -> !config.isSuperPeerEnabled() || superPeerConnected.blockingFirst(),
                                        peerCommunicationConsumer),
                                config.getServerChannelInitializer())));
    }

    public Server(DrasylConfig config,
                  PeerChannelGroup channelGroup,
                  AtomicBoolean opened,
                  Set<URI> nodeEndpoints,
                  ServerBootstrap serverBootstrap) {
        this.config = config;
        this.channel = null;
        this.channelGroup = channelGroup;
        this.serverBootstrap = serverBootstrap;
        this.opened = opened;
        this.actualPort = -1;
        this.actualEndpoints = new HashSet<>();
        this.nodeEndpoints = nodeEndpoints;
    }

    private static ServerChannelInitializer initiateChannelInitializer(
            ServerEnvironment environment,
            Class<? extends ChannelInitializer<SocketChannel>> clazz) throws ServerException {
        try {
            Constructor<?> constructor = clazz.getConstructor(ServerEnvironment.class);
            return (ServerChannelInitializer) constructor.newInstance(environment);
        }
        catch (NoSuchMethodException e) {
            throw new ServerException("The given channel initializer has not the correct signature: '" + clazz + "'");
        }
        catch (IllegalAccessException e) {
            throw new ServerException("Can't access the given channel initializer: '" + clazz + "'");
        }
        catch (InvocationTargetException e) {
            throw new ServerException("Can't invoke the given channel initializer: '" + clazz + "'");
        }
        catch (InstantiationException e) {
            throw new ServerException("Can't instantiate the given channel initializer: '" + clazz + "'");
        }
    }

    /**
     * Starts the server.
     */
    @SuppressWarnings({ "java:S3776" })
    @Override
    public void open() throws ServerException {
        if (opened.compareAndSet(false, true)) {
            LOG.debug("Start Server...");
            try {
                ChannelFuture channelFuture = serverBootstrap
                        .bind(config.getServerBindHost(), config.getServerBindPort());
                channelFuture.awaitUninterruptibly();

                if (channelFuture.isSuccess()) {
                    channel = channelFuture.channel();

                    channel.closeFuture().addListener(future -> {
                        nodeEndpoints.removeAll(actualEndpoints);
                        actualPort = -1;
                        actualEndpoints = Set.of();
                    });

                    InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
                    actualPort = socketAddress.getPort();
                    actualEndpoints = determineActualEndpoints(config, socketAddress);
                    nodeEndpoints.addAll(actualEndpoints);
                }
                else {
                    throw new ServerException("Unable to start server: " + channelFuture.cause().getMessage());
                }
            }
            catch (IllegalArgumentException e) {
                throw new ServerException("Unable to get channel: " + e.getMessage());
            }
        }
    }

    static Set<URI> determineActualEndpoints(DrasylConfig config, InetSocketAddress listenAddress) {
        Set<URI> configEndpoints = config.getServerEndpoints();
        if (!configEndpoints.isEmpty()) {
            // read endpoints from config
            return configEndpoints.stream().map(uri -> {
                if (uri.getPort() == 0) {
                    return overridePort(uri, listenAddress.getPort());
                }
                return uri;
            }).collect(Collectors.toSet());
        }

        Set<String> addresses;
        if (isMatchAllAddress(listenAddress.getAddress().getHostAddress())) {
            // use all available addresses
            addresses = getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(listenAddress.getHostName());
        }
        String scheme = config.getServerSSLEnabled() ? "wss" : "ws";
        return addresses.stream().map(a -> URI.create(scheme + "://" + a + ":" + listenAddress.getPort())).collect(Collectors.toSet());
    }

    /**
     * Closes the server socket and all open client sockets.
     */
    @Override
    @SuppressWarnings({ "java:S1905" })
    public void close() {
        if (opened.compareAndSet(true, false) && channel != null && channel.isOpen()) {
            LOG.info("Stop Server listening at {}:{}...", config.getServerBindHost(), actualPort);
            // shutdown server
            channel.close();
            channel = null;
            LOG.info("Server stopped");
        }
    }
}
