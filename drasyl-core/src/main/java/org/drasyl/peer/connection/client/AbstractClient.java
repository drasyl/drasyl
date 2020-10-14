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
package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.DrasylFunction;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

abstract class AbstractClient implements DrasylNodeComponent {
    private final EventLoopGroup workerGroup;
    private final Supplier<Set<Endpoint>> endpointsSupplier;
    private final AtomicBoolean opened;
    private final AtomicInteger nextEndpointPointer;
    private final AtomicInteger nextRetryDelayPointer;
    private final DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier;
    private final List<Duration> retryDelays;
    protected Channel channel;
    protected BooleanSupplier acceptNewConnectionsSupplier;

    @SuppressWarnings({ "java:S107" })
    protected AbstractClient(final List<Duration> retryDelays,
                             final EventLoopGroup workerGroup,
                             final Supplier<Set<Endpoint>> endpointsSupplier,
                             final BooleanSupplier acceptNewConnectionsSupplier,
                             final Identity identity,
                             final Pipeline pipeline,
                             final PeersManager peersManager,
                             final DrasylConfig config,
                             final PeerChannelGroup channelGroup,
                             final short idleRetries,
                             final Duration idleTimeout,
                             final Duration handshakeTimeout,
                             final boolean joinsAsChildren,
                             final Class<? extends ChannelInitializer<SocketChannel>> channelInitializerClazz) {
        this(
                retryDelays,
                workerGroup,
                endpointsSupplier,
                acceptNewConnectionsSupplier,
                endpoint -> new Bootstrap()
                        .group(workerGroup)
                        .channel(getBestSocketChannel())
                        .handler(initiateChannelInitializer(new ClientEnvironment(
                                        config,
                                        identity,
                                        endpoint,
                                        pipeline,
                                        channelGroup,
                                        peersManager,
                                        joinsAsChildren,
                                        idleRetries,
                                        idleTimeout,
                                        handshakeTimeout
                                ),
                                channelInitializerClazz))
                        .remoteAddress(endpoint.getHost(), endpoint.getPort())
        );
    }

    /**
     * Returns the {@link SocketChannel} that fits best to the current environment. Under Linux the
     * more performant {@link EpollSocketChannel} is returned.
     *
     * @return {@link SocketChannel} that fits best to the current environment
     */
    static Class<? extends SocketChannel> getBestSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        else {
            return NioSocketChannel.class;
        }
    }

    protected AbstractClient(final List<Duration> retryDelays,
                             final EventLoopGroup workerGroup,
                             final Supplier<Set<Endpoint>> endpointsSupplier,
                             final BooleanSupplier acceptNewConnectionsSupplier,
                             final DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier) {
        this(
                retryDelays,
                workerGroup,
                endpointsSupplier,
                new AtomicBoolean(false),
                acceptNewConnectionsSupplier,
                new AtomicInteger(0),
                new AtomicInteger(0),
                bootstrapSupplier,
                null
        );
    }

    @SuppressWarnings({ "java:S107" })
    protected AbstractClient(final List<Duration> retryDelays,
                             final EventLoopGroup workerGroup,
                             final Supplier<Set<Endpoint>> endpointsSupplier,
                             final AtomicBoolean opened,
                             final BooleanSupplier acceptNewConnectionsSupplier,
                             final AtomicInteger nextEndpointPointer,
                             final AtomicInteger nextRetryDelayPointer,
                             final DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier,
                             final Channel channel) {
        this.retryDelays = retryDelays;
        this.workerGroup = workerGroup;
        this.endpointsSupplier = endpointsSupplier;
        this.opened = opened;
        this.acceptNewConnectionsSupplier = acceptNewConnectionsSupplier;
        this.nextEndpointPointer = nextEndpointPointer;
        this.nextRetryDelayPointer = nextRetryDelayPointer;
        this.bootstrapSupplier = bootstrapSupplier;
        this.channel = channel;
    }

    @Override
    public void open() {
        if (opened.compareAndSet(false, true)) {
            getLogger().debug("Start Client...");
            connect(nextEndpoint());
            getLogger().debug("Client started");
        }
    }

    void connect(final Endpoint endpoint) {
        if (endpoint == null) {
            getLogger().debug("No endpoint present. Permanently unable to connect to Server.");
            failed();
            return;
        }

        getLogger().debug("Connect to Endpoint '{}'", endpoint);
        try {
            bootstrapSupplier.apply(endpoint).connect()
                    .addListener((ChannelFutureListener) channelFuture -> { // NOSONAR
                        if (channelFuture.isSuccess()) {
                            getLogger().debug("Connection to Endpoint '{}' established", endpoint);
                            channel = channelFuture.channel();
                            channel.closeFuture().addListener(future -> {
                                getLogger().debug("Connection to Endpoint '{}' closed", endpoint);
                                conditionalScheduledReconnect();
                            });
                        }
                        else {
                            getLogger().debug("Error while trying to connect to Endpoint '{}': {}", endpoint, channelFuture.cause().getMessage());
                            conditionalScheduledReconnect();
                        }
                    });
        }
        catch (final DrasylException | IllegalArgumentException e) {
            getLogger().warn("Unable to create channel initializer:", e);
            conditionalScheduledReconnect();
        }
    }

    /**
     * @return the next peer's endpoint. Returns <code>null</code> if no endpoint is present
     */
    Endpoint nextEndpoint() {
        try {
            final List<Endpoint> endpoints = new ArrayList<>(endpointsSupplier.get());
            final Endpoint endpoint = endpoints.get(nextEndpointPointer.get());
            nextEndpointPointer.updateAndGet(p -> (p + 1) % endpoints.size());
            return endpoint;
        }
        catch (final IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * If {@link #shouldRetry()} returns <code>true</code>, an attempt to (re)connect to the Server
     * is scheduled.
     */
    void conditionalScheduledReconnect() {
        if (shouldRetry()) {
            final Duration duration = nextRetryDelay();
            getLogger().debug("Wait {}ms before reconnect", duration.toMillis());
            workerGroup.schedule(() -> {
                if (opened.get()) {
                    connect(nextEndpoint());
                }
            }, duration.toMillis(), MILLISECONDS);
        }
        else {
            failed();
        }
    }

    /**
     * @return <code>true</code> if the client should attempt to reconnect. Otherwise
     * <code>false</code> is returned
     */
    protected boolean shouldRetry() {
        return opened.get() && acceptNewConnectionsSupplier.getAsBoolean() && !retryDelays.isEmpty();
    }

    /**
     * This method is called if a connection was not possible and no further connection attempts
     * will be made.
     */
    protected void failed() {
        // do nothing
    }

    /**
     * @return the duration of delay before the client should make a new attempt to reconnect to
     * Server. Iterates over list of all delays specified in configuration. Uses last element
     * permanently when end of list is reached.
     * @throws IllegalArgumentException if list is empty
     */
    Duration nextRetryDelay() {
        final Duration retryDelay = retryDelays.get(nextRetryDelayPointer.get());
        nextRetryDelayPointer.updateAndGet(p -> Math.min(p + 1, retryDelays.size() - 1));
        return retryDelay;
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false) && channel != null) {
            // close connection
            getLogger().debug("Stop Client...");
            channel.close().syncUninterruptibly();
            channel = null;
            getLogger().debug("Client stopped");
        }
    }

    protected static ClientChannelInitializer initiateChannelInitializer(
            final ClientEnvironment environment,
            final Class<? extends ChannelInitializer<SocketChannel>> clazz) throws ClientException {
        try {
            final Constructor<?> constructor = clazz.getConstructor(ClientEnvironment.class);
            return (ClientChannelInitializer) constructor.newInstance(environment);
        }
        catch (final NoSuchMethodException e) {
            throw new ClientException("The given channel initializer has not the correct signature: '" + clazz + "'");
        }
        catch (final IllegalAccessException e) {
            throw new ClientException("Can't access the given channel initializer: '" + clazz + "'");
        }
        catch (final InvocationTargetException e) {
            throw new ClientException("Can't invoke the given channel initializer: '" + clazz + "'");
        }
        catch (final InstantiationException e) {
            throw new ClientException("Can't instantiate the given channel initializer: '" + clazz + "'");
        }
    }

    protected abstract Logger getLogger();
}