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
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.QuitMessage;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.drasyl.util.NetworkUtil.isMatchAllAddress;
import static org.drasyl.util.UriUtil.overridePort;

@SuppressWarnings({ "squid:S00107" })
public class Server implements AutoCloseable {
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    public final ServerBootstrap serverBootstrap;
    private final Supplier<Identity> identitySupplier;
    private final PeersManager peersManager;
    private final DrasylConfig config;
    private final Messenger messenger;
    private final AtomicBoolean opened;
    protected final ServerChannelGroup channelGroup;
    protected final ChannelInitializer<SocketChannel> channelInitializer;
    private Channel channel;
    private int actualPort;
    private Set<URI> actualEndpoints;

    Server(Supplier<Identity> identitySupplier,
           Messenger messenger,
           PeersManager peersManager,
           DrasylConfig config,
           ServerBootstrap serverBootstrap,
           EventLoopGroup workerGroup,
           EventLoopGroup bossGroup,
           ChannelInitializer<SocketChannel> channelInitializer,
           AtomicBoolean opened,
           ServerChannelGroup channelGroup,
           int actualPort, Channel channel,
           Set<URI> actualEndpoints) {
        this.identitySupplier = identitySupplier;
        this.peersManager = peersManager;
        this.config = config;
        this.channel = channel;
        this.serverBootstrap = serverBootstrap;
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.channelInitializer = channelInitializer;
        this.opened = opened;
        this.messenger = messenger;
        this.actualPort = actualPort;
        this.actualEndpoints = actualEndpoints;
        this.channelGroup = channelGroup;
    }

    public Server(Supplier<Identity> identitySupplier,
                  Messenger messenger,
                  PeersManager peersManager,
                  DrasylConfig config,
                  EventLoopGroup workerGroup,
                  EventLoopGroup bossGroup,
                  Observable<Boolean> superPeerConnected,
                  Consumer<CompressedPublicKey> peerCommunicationConsumer) throws ServerException {
        this(identitySupplier, messenger, peersManager, config, workerGroup, bossGroup, superPeerConnected, new AtomicBoolean(false), peerCommunicationConsumer);
    }

    /**
     * Node server for forwarding messages to child peers.
     *
     * @param identitySupplier          the identity manager
     * @param messenger                 the messenger object
     * @param peersManager              the peers manager
     * @param config                    config that should be used
     * @param workerGroup               netty shared worker group
     * @param bossGroup                 netty shared boss group
     * @param superPeerConnected
     * @param peerCommunicationConsumer
     */
    public Server(Supplier<Identity> identitySupplier,
                  Messenger messenger,
                  PeersManager peersManager,
                  DrasylConfig config,
                  EventLoopGroup workerGroup,
                  EventLoopGroup bossGroup,
                  Observable<Boolean> superPeerConnected,
                  AtomicBoolean opened,
                  Consumer<CompressedPublicKey> peerCommunicationConsumer) throws ServerException {
        this.identitySupplier = identitySupplier;
        this.peersManager = peersManager;
        this.config = config;
        this.channel = null;
        this.serverBootstrap = new ServerBootstrap();
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.channelGroup = new ServerChannelGroup();
        this.channelInitializer = initiateChannelInitializer(
                new ServerEnvironment(
                        config,
                        identitySupplier,
                        peersManager,
                        messenger,
                        this::getEndpoints,
                        channelGroup,
                        () -> this.isOpen() && (!config.isSuperPeerEnabled() || superPeerConnected.blockingFirst()),
                        peerCommunicationConsumer),
                config.getServerChannelInitializer()
        );
        this.opened = opened;
        this.messenger = messenger;
        this.actualPort = -1;
        this.actualEndpoints = new HashSet<>();
    }

    /**
     * @return the endpoints
     */
    public Set<URI> getEndpoints() {
        return actualEndpoints;
    }

    public boolean isOpen() {
        return opened.get();
    }

    ServerChannelGroup getChannelGroup() {
        return channelGroup;
    }

    Identity getIdentity() {
        return identitySupplier.get();
    }

    /**
     * Starts the relay server.
     */
    @SuppressWarnings({ "java:S3776" })
    public void open() throws ServerException {
        if (opened.compareAndSet(false, true)) {
            try {
                ChannelFuture channelFuture = serverBootstrap
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(channelInitializer)
                        .bind(config.getServerBindHost(), config.getServerBindPort());
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
                    actualEndpoints = determineActualEndpoints(config, socketAddress);

                    messenger.setServerSink(message -> {
                        CompressedPublicKey recipient = message.getRecipient();

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
                    throw new ServerException("Unable to start server: " + channelFuture.cause().getMessage());
                }
            }
            catch (IllegalArgumentException e) {
                throw new ServerException("Unable to get channel: " + e.getMessage());
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
}
