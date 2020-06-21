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
import org.drasyl.DrasylNodeConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.util.NetworkUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.util.UriUtil.overridePort;

@SuppressWarnings({ "squid:S00107" })
public class NodeServer implements AutoCloseable {
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    public final ServerBootstrap serverBootstrap;
    private final Supplier<Identity> identitySupplier;
    private final PeersManager peersManager;
    private final DrasylNodeConfig config;
    private final Messenger messenger;
    private final AtomicBoolean opened;
    private final NodeServerChannelGroup channelGroup;
    private final ChannelInitializer<SocketChannel> channelInitializer;
    private Channel channel;
    private int actualPort;
    private Set<URI> actualEndpoints;

    NodeServer(Supplier<Identity> identitySupplier,
               Messenger messenger,
               PeersManager peersManager,
               DrasylNodeConfig config,
               ServerBootstrap serverBootstrap,
               EventLoopGroup workerGroup,
               EventLoopGroup bossGroup,
               ChannelInitializer<SocketChannel> channelInitializer,
               AtomicBoolean opened,
               NodeServerChannelGroup channelGroup,
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

    /**
     * Node server for forwarding messages to child peers.
     *
     * @param identitySupplier   the identity manager
     * @param messenger          the messenger object
     * @param peersManager       the peers manager
     * @param config             config that should be used
     * @param workerGroup        netty shared worker group
     * @param bossGroup          netty shared boss group
     * @param superPeerConnected
     */
    public NodeServer(Supplier<Identity> identitySupplier,
                      Messenger messenger,
                      PeersManager peersManager,
                      DrasylNodeConfig config,
                      EventLoopGroup workerGroup,
                      EventLoopGroup bossGroup,
                      Observable<Boolean> superPeerConnected) throws NodeServerException {
        this.identitySupplier = identitySupplier;
        this.peersManager = peersManager;
        this.config = config;
        this.channel = null;
        this.serverBootstrap = new ServerBootstrap();
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.channelGroup = new NodeServerChannelGroup();
        this.channelInitializer = initiateChannelInitializer(
                new NodeServerEnvironment(
                        config,
                        identitySupplier,
                        peersManager,
                        messenger,
                        this::getEndpoints,
                        channelGroup,
                        () -> this.isOpen() && (!config.isSuperPeerEnabled() || superPeerConnected.blockingFirst())
                ),
                config.getServerChannelInitializer()
        );
        this.opened = new AtomicBoolean(false);
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

    NodeServerChannelGroup getChannelGroup() {
        return channelGroup;
    }

    Identity getIdentity() {
        return identitySupplier.get();
    }

    /**
     * Starts the relay server.
     */
    @SuppressWarnings({ "java:S3776" })
    public void open() throws NodeServerException {
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

                    actualEndpoints = config.getServerEndpoints().stream()
                            .map(uri -> {
                                if (uri.getPort() == 0) {
                                    return overridePort(uri, actualPort);
                                }
                                return uri;
                            }).collect(Collectors.toSet());
                    if (actualEndpoints.isEmpty()) {
                        String scheme = config.getServerSSLEnabled() ? "wss" : "ws";
                        actualEndpoints = NetworkUtil.getAddresses().stream().map(a -> URI.create(scheme + "://" + a + ":" + actualPort)).collect(Collectors.toSet());
                    }

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
            catch (IllegalArgumentException e) {
                throw new NodeServerException("Unable to get channel: " + e.getMessage());
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

    private static ChannelInitializer<SocketChannel> initiateChannelInitializer(
            NodeServerEnvironment environment,
            Class<? extends ChannelInitializer<SocketChannel>> clazz) throws NodeServerException {
        try {
            Constructor<?> constructor = clazz.getConstructor(NodeServerEnvironment.class);
            return (ChannelInitializer<SocketChannel>) constructor.newInstance(environment);
        }
        catch (NoSuchMethodException e) {
            throw new NodeServerException("The given channel initializer has not the correct signature: '" + clazz + "'");
        }
        catch (IllegalAccessException e) {
            throw new NodeServerException("Can't access the given channel initializer: '" + clazz + "'");
        }
        catch (InvocationTargetException e) {
            throw new NodeServerException("Can't invoke the given channel initializer: '" + clazz + "'");
        }
        catch (InstantiationException e) {
            throw new NodeServerException("Can't instantiate the given channel initializer: '" + clazz + "'");
        }
    }
}
