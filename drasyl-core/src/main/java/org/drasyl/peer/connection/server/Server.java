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
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.drasyl.util.UriUtil.createUri;
import static org.drasyl.util.UriUtil.overridePort;

/**
 * The server binds to a defined port and thus allows the node to be discovered and contacted by
 * other peers.
 */
@SuppressWarnings({ "squid:S00107" })
public class Server implements DrasylNodeComponent {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private final ServerBootstrap serverBootstrap;
    private final DrasylConfig config;
    private final AtomicBoolean opened;
    private final Set<URI> nodeEndpoints;
    private Channel channel;
    private Set<URI> actualEndpoints;
    private InetSocketAddress socketAddress;

    Server(DrasylConfig config,
           ServerBootstrap serverBootstrap,
           AtomicBoolean opened,
           InetSocketAddress socketAddress,
           Channel channel,
           Set<URI> actualEndpoints,
           Set<URI> nodeEndpoints) {
        this.config = config;
        this.channel = channel;
        this.serverBootstrap = serverBootstrap;
        this.opened = opened;
        this.socketAddress = socketAddress;
        this.actualEndpoints = actualEndpoints;
        this.nodeEndpoints = nodeEndpoints;
    }

    public Server(Identity identity,
                  Messenger messenger,
                  PeersManager peersManager,
                  DrasylConfig config,
                  PeerChannelGroup channelGroup,
                  EventLoopGroup workerGroup,
                  EventLoopGroup bossGroup,
                  Set<URI> nodeEndpoints,
                  BooleanSupplier acceptNewConnectionsSupplier) throws ServerException {
        this(identity, messenger, peersManager, config, channelGroup, workerGroup, bossGroup, new AtomicBoolean(false), acceptNewConnectionsSupplier, nodeEndpoints);
    }

    /**
     * Server for accepting connections from child peers and non-child peers.
     *
     * @param identity                     the identity manager
     * @param messenger                    the messenger object
     * @param peersManager                 the peers manager
     * @param config                       config that should be used
     * @param channelGroup                 the channel group
     * @param workerGroup                  netty shared worker group
     * @param bossGroup                    netty shared boss group
     * @param acceptNewConnectionsSupplier the accept new connections supplier
     * @param nodeEndpoints                the node endpoints
     */
    public Server(Identity identity,
                  Messenger messenger,
                  PeersManager peersManager,
                  DrasylConfig config,
                  PeerChannelGroup channelGroup,
                  EventLoopGroup workerGroup,
                  EventLoopGroup bossGroup,
                  AtomicBoolean opened,
                  BooleanSupplier acceptNewConnectionsSupplier,
                  Set<URI> nodeEndpoints) throws ServerException {
        this(
                config,
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
                                        acceptNewConnectionsSupplier
                                ),
                                config.getServerChannelInitializer())));
    }

    public Server(DrasylConfig config,
                  AtomicBoolean opened,
                  Set<URI> nodeEndpoints,
                  ServerBootstrap serverBootstrap) {
        this.config = config;
        this.channel = null;
        this.serverBootstrap = serverBootstrap;
        this.opened = opened;
        this.socketAddress = null;
        this.actualEndpoints = new HashSet<>();
        this.nodeEndpoints = nodeEndpoints;
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
                        socketAddress = null;
                        actualEndpoints = Set.of();
                    });

                    socketAddress = (InetSocketAddress) channel.localAddress();
                    actualEndpoints = determineActualEndpoints(config, socketAddress);
                    nodeEndpoints.addAll(actualEndpoints);

                    LOG.debug("Server started and listening at {}", socketAddress);
                }
                else {
                    throw new ServerException("Unable to bind server to address " + config.getServerBindHost() + ":" + config.getServerBindPort() + ": " + channelFuture.cause().getMessage());
                }
            }
            catch (IllegalArgumentException e) {
                throw new ServerException("Unable to get channel: " + e.getMessage());
            }
        }
    }

    /**
     * Closes the server socket and all open client sockets.
     */
    @Override
    @SuppressWarnings({ "java:S1905" })
    public void close() {
        if (opened.compareAndSet(true, false) && channel != null && channel.isOpen()) {
            LOG.info("Stop Server listening at {}...", socketAddress);
            // shutdown server
            channel.close().awaitUninterruptibly();
            channel = null;
            LOG.info("Server stopped");
        }
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

        Set<InetAddress> addresses;
        if (listenAddress.getAddress().isAnyLocalAddress()) {
            // use all available addresses
            addresses = getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(listenAddress.getAddress());
        }
        String scheme = config.getServerSSLEnabled() ? "wss" : "ws";
        return addresses.stream().map(address -> createUri(scheme, address.getHostAddress(), listenAddress.getPort())).collect(Collectors.toSet());
    }
}