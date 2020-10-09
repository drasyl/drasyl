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
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.Pair;
import org.drasyl.util.PortMappingUtil;
import org.drasyl.util.PortMappingUtil.PortMapping;
import org.drasyl.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.drasyl.util.ObservableUtil.pairWithPreviousObservable;
import static org.drasyl.util.PortMappingUtil.Protocol.TCP;
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
    private final Identity identity;
    private final DrasylConfig config;
    private final AtomicBoolean opened;
    private final Set<Endpoint> nodeEndpoints;
    private final Scheduler scheduler;
    private final Function<InetSocketAddress, Set<PortMapping>> portExposer;
    private Channel channel;
    private Set<Endpoint> actualEndpoints;
    private InetSocketAddress socketAddress;
    private Set<PortMapping> portMappings;

    Server(final Identity identity,
           final DrasylConfig config,
           final ServerBootstrap serverBootstrap,
           final AtomicBoolean opened,
           final InetSocketAddress socketAddress,
           final Channel channel,
           final Set<Endpoint> actualEndpoints,
           final Set<Endpoint> nodeEndpoints,
           final Scheduler scheduler,
           final Function<InetSocketAddress, Set<PortMapping>> portExposer) {
        this.identity = identity;
        this.config = config;
        this.channel = channel;
        this.serverBootstrap = serverBootstrap;
        this.opened = opened;
        this.socketAddress = socketAddress;
        this.actualEndpoints = actualEndpoints;
        this.nodeEndpoints = nodeEndpoints;
        this.scheduler = scheduler;
        this.portExposer = portExposer;
    }

    public Server(final Identity identity,
                  final Pipeline pipeline,
                  final PeersManager peersManager,
                  final DrasylConfig config,
                  final PeerChannelGroup channelGroup,
                  final EventLoopGroup workerGroup,
                  final EventLoopGroup bossGroup,
                  final Set<Endpoint> nodeEndpoints,
                  final BooleanSupplier acceptNewConnectionsSupplier) throws ServerException {
        this(identity, pipeline, peersManager, config, channelGroup, workerGroup, bossGroup, new AtomicBoolean(false), acceptNewConnectionsSupplier, nodeEndpoints);
    }

    /**
     * Server for accepting connections from child peers and non-child peers.
     *
     * @param identity                     the identity manager
     * @param pipeline                     the pipeline object
     * @param peersManager                 the peers manager
     * @param config                       config that should be used
     * @param channelGroup                 the channel group
     * @param workerGroup                  netty shared worker group
     * @param bossGroup                    netty shared boss group
     * @param acceptNewConnectionsSupplier the accept new connections supplier
     * @param nodeEndpoints                the node endpoints
     */
    public Server(final Identity identity,
                  final Pipeline pipeline,
                  final PeersManager peersManager,
                  final DrasylConfig config,
                  final PeerChannelGroup channelGroup,
                  final EventLoopGroup workerGroup,
                  final EventLoopGroup bossGroup,
                  final AtomicBoolean opened,
                  final BooleanSupplier acceptNewConnectionsSupplier,
                  final Set<Endpoint> nodeEndpoints) throws ServerException {
        this(
                identity,
                config,
                new ServerBootstrap().group(bossGroup, workerGroup)
                        .channel(getBestServerSocketChannel())
                        .childHandler(initiateChannelInitializer(new ServerEnvironment(
                                        config,
                                        identity,
                                        peersManager,
                                        pipeline,
                                        nodeEndpoints,
                                        channelGroup,
                                        acceptNewConnectionsSupplier
                                ),
                                config.getServerChannelInitializer())),
                opened,
                null,
                null,
                new HashSet<>(),
                nodeEndpoints,
                DrasylScheduler.getInstanceHeavy(),
                address -> PortMappingUtil.expose(address, TCP));
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
                final ChannelFuture channelFuture = serverBootstrap
                        .bind(config.getServerBindHost(), config.getServerBindPort());
                channelFuture.awaitUninterruptibly();

                if (channelFuture.isSuccess()) {
                    channel = channelFuture.channel();

                    channel.closeFuture().addListener(future -> {
                        unexposeEndpoints();
                        nodeEndpoints.removeAll(actualEndpoints);
                        socketAddress = null;
                        actualEndpoints = Set.of();
                    });

                    socketAddress = (InetSocketAddress) channel.localAddress();
                    actualEndpoints = determineActualEndpoints(identity, config, socketAddress);
                    nodeEndpoints.addAll(actualEndpoints);

                    LOG.debug("Server started and listening at {}", socketAddress);

                    if (config.isServerExposeEnabled()) {
                        exposeEndpoints(socketAddress);
                    }
                }
                else {
                    throw new ServerException("Unable to bind server to address " + config.getServerBindHost() + ":" + config.getServerBindPort() + ": " + channelFuture.cause().getMessage());
                }
            }
            catch (final IllegalArgumentException e) {
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
            LOG.debug("Stop Server listening at {}...", socketAddress);
            // shutdown server
            channel.close().awaitUninterruptibly();
            channel = null;
            LOG.debug("Server stopped");
        }
    }

    void exposeEndpoints(final InetSocketAddress socketAddress) {
        scheduler.scheduleDirect(() -> {
            // create port mappings
            portMappings = portExposer.apply(socketAddress);

            // we need to observe these mappings because they can change or fail over time
            final List<Observable<Optional<InetSocketAddress>>> externalAddressObservables = portMappings.stream().map(PortMapping::externalAddress).collect(Collectors.toList());
            @SuppressWarnings("unchecked") final Observable<Set<InetSocketAddress>> allExternalAddressesObservable = Observable.combineLatest(
                    externalAddressObservables,
                    newMappings -> Arrays.stream(newMappings).map(o -> (Optional<InetSocketAddress>) o).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet())
            );
            final Observable<Pair<Set<InetSocketAddress>, Set<InetSocketAddress>>> allExternalAddressesChangesObservable = pairWithPreviousObservable(allExternalAddressesObservable);

            final String scheme = config.getServerSSLEnabled() ? "wss" : "ws";
            allExternalAddressesChangesObservable.subscribe(pair -> {
                final Set<InetSocketAddress> currentAddresses = pair.first();
                final Set<InetSocketAddress> previousAddresses = Optional.ofNullable(pair.second()).orElse(Set.of());

                final Set<InetSocketAddress> addressesToRemove = SetUtil.difference(previousAddresses, currentAddresses);
                final Set<Endpoint> endpointsToRemove = addressesToRemove.stream()
                        .map(address -> createUri(scheme, address.getHostName(), address.getPort()))
                        .map(address -> Endpoint.of(address, identity.getPublicKey()))
                        .collect(Collectors.toSet());
                nodeEndpoints.removeAll(endpointsToRemove);

                final Set<InetSocketAddress> addressesToAdd = SetUtil.difference(currentAddresses, previousAddresses);
                final Set<Endpoint> endpointsToAdd = addressesToAdd.stream()
                        .map(address -> createUri(scheme, address.getHostName(), address.getPort()))
                        .map(address -> Endpoint.of(address, identity.getPublicKey()))
                        .collect(Collectors.toSet());
                nodeEndpoints.addAll(endpointsToAdd);
            });
        });
    }

    private void unexposeEndpoints() {
        scheduler.scheduleDirect(() -> {
            if (portMappings != null) {
                portMappings.forEach(PortMapping::close);
                portMappings = null;
            }
        });
    }

    private static ServerChannelInitializer initiateChannelInitializer(
            final ServerEnvironment environment,
            final Class<? extends ChannelInitializer<SocketChannel>> clazz) throws ServerException {
        try {
            final Constructor<?> constructor = clazz.getConstructor(ServerEnvironment.class);
            return (ServerChannelInitializer) constructor.newInstance(environment);
        }
        catch (final NoSuchMethodException e) {
            throw new ServerException("The given channel initializer has not the correct signature: '" + clazz + "'");
        }
        catch (final IllegalAccessException e) {
            throw new ServerException("Can't access the given channel initializer: '" + clazz + "'");
        }
        catch (final InvocationTargetException e) {
            throw new ServerException("Can't invoke the given channel initializer: '" + clazz + "'");
        }
        catch (final InstantiationException e) {
            throw new ServerException("Can't instantiate the given channel initializer: '" + clazz + "'");
        }
    }

    static Set<Endpoint> determineActualEndpoints(final Identity identity,
                                                  final DrasylConfig config,
                                                  final InetSocketAddress listenAddress) {
        final Set<Endpoint> configEndpoints = config.getServerEndpoints();
        if (!configEndpoints.isEmpty()) {
            // read endpoints from config
            return configEndpoints.stream().map(endpoint -> {
                if (endpoint.getPort() == 0) {
                    return Endpoint.of(overridePort(endpoint.getURI(), listenAddress.getPort()), identity.getPublicKey());
                }
                return endpoint;
            }).collect(Collectors.toSet());
        }

        final Set<InetAddress> addresses;
        if (listenAddress.getAddress().isAnyLocalAddress()) {
            // use all available addresses
            addresses = getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(listenAddress.getAddress());
        }
        final String scheme = config.getServerSSLEnabled() ? "wss" : "ws";
        return addresses.stream()
                .map(address -> createUri(scheme, address.getHostAddress(), listenAddress.getPort()))
                .map(address -> Endpoint.of(address, identity.getPublicKey()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the {@link ServerSocketChannel} that fits best to the current environment. Under
     * Linux the more performant {@link EpollServerSocketChannel} is returned.
     *
     * @return {@link ServerSocketChannel} that fits best to the current environment
     */
    static Class<? extends ServerSocketChannel> getBestServerSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        else {
            return NioServerSocketChannel.class;
        }
    }
}