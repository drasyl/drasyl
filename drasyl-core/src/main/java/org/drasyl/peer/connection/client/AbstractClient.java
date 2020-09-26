package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.util.DrasylFunction;
import org.drasyl.util.SetUtil;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Supplier<Set<Endpoint>> endpointsSupplier,
                             BooleanSupplier acceptNewConnectionsSupplier,
                             Identity identity,
                             Messenger messenger,
                             PeersManager peersManager,
                             DrasylConfig config,
                             PeerChannelGroup channelGroup,
                             short idleRetries,
                             Duration idleTimeout,
                             Duration handshakeTimeout,
                             Consumer<Event> eventConsumer,
                             boolean joinsAsChildren,
                             Class<? extends ChannelInitializer<SocketChannel>> channelInitializerClazz) {
        this(
                retryDelays,
                workerGroup,
                endpointsSupplier,
                acceptNewConnectionsSupplier,
                endpoint -> new Bootstrap()
                        .group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .handler(initiateChannelInitializer(new ClientEnvironment(
                                        config,
                                        identity,
                                        endpoint,
                                        messenger,
                                        channelGroup,
                                        peersManager,
                                        eventConsumer,
                                        joinsAsChildren,
                                        idleRetries,
                                        idleTimeout,
                                        handshakeTimeout
                                ),
                                channelInitializerClazz))
                        .remoteAddress(endpoint.getHost(), endpoint.getPort())
        );
    }

    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Supplier<Set<Endpoint>> endpointsSupplier,
                             BooleanSupplier acceptNewConnectionsSupplier,
                             DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier) {
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
    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Supplier<Set<Endpoint>> endpointsSupplier,
                             AtomicBoolean opened,
                             BooleanSupplier acceptNewConnectionsSupplier,
                             AtomicInteger nextEndpointPointer,
                             AtomicInteger nextRetryDelayPointer,
                             DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier,
                             Channel channel) {
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

    void connect(Endpoint endpoint) {
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
        catch (DrasylException | IllegalArgumentException e) {
            getLogger().warn("Unable to create channel initializer:", e);
            conditionalScheduledReconnect();
        }
    }

    /**
     * @return the next peer's endpoint. Returns <code>null</code> if no endpoint is present
     */
    Endpoint nextEndpoint() {
        try {
            Set<Endpoint> endpoints = endpointsSupplier.get();
            Endpoint endpoint = SetUtil.nthElement(endpoints, nextEndpointPointer.get());
            nextEndpointPointer.updateAndGet(p -> (p + 1) % endpoints.size());
            return endpoint;
        }
        catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * If {@link #shouldRetry()} returns <code>true</code>, an attempt to (re)connect to the Server
     * is scheduled.
     */
    void conditionalScheduledReconnect() {
        if (shouldRetry()) {
            Duration duration = nextRetryDelay();
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
        Duration retryDelay = retryDelays.get(nextRetryDelayPointer.get());
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
            ClientEnvironment environment,
            Class<? extends ChannelInitializer<SocketChannel>> clazz) throws ClientException {
        try {
            Constructor<?> constructor = clazz.getConstructor(ClientEnvironment.class);
            return (ClientChannelInitializer) constructor.newInstance(environment);
        }
        catch (NoSuchMethodException e) {
            throw new ClientException("The given channel initializer has not the correct signature: '" + clazz + "'");
        }
        catch (IllegalAccessException e) {
            throw new ClientException("Can't access the given channel initializer: '" + clazz + "'");
        }
        catch (InvocationTargetException e) {
            throw new ClientException("Can't invoke the given channel initializer: '" + clazz + "'");
        }
        catch (InstantiationException e) {
            throw new ClientException("Can't instantiate the given channel initializer: '" + clazz + "'");
        }
    }

    protected abstract Logger getLogger();
}