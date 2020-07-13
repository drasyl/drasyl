package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylException;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.util.DrasylFunction;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.SetUtil;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.util.WebSocketUtil.webSocketPort;

abstract class AbstractClient implements AutoCloseable {
    private final EventLoopGroup workerGroup;
    private final Supplier<Set<URI>> endpointsSupplier;
    private final AtomicBoolean opened;
    private final AtomicInteger nextEndpointPointer;
    private final AtomicInteger nextRetryDelayPointer;
    private final Supplier<Bootstrap> bootstrapSupplier;
    private final Subject<Boolean> connected;
    private final DrasylFunction<URI, ChannelInitializer<SocketChannel>, DrasylException> channelInitializerSupplier;
    private final List<Duration> retryDelays;
    protected ChannelInitializer<SocketChannel> channelInitializer;
    protected Channel channel;

    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Supplier<Set<URI>> endpointsSupplier,
                             DrasylFunction<URI, ChannelInitializer<SocketChannel>, DrasylException> channelInitializerSupplier) {
        this(
                retryDelays,
                workerGroup,
                endpointsSupplier,
                BehaviorSubject.createDefault(false),
                channelInitializerSupplier
        );
    }

    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Supplier<Set<URI>> endpointsSupplier,
                             Subject<Boolean> connected,
                             DrasylFunction<URI, ChannelInitializer<SocketChannel>, DrasylException> channelInitializerSupplier) {
        this(
                retryDelays,
                workerGroup,
                endpointsSupplier,
                new AtomicBoolean(false),
                new AtomicInteger(0),
                new AtomicInteger(0),
                Bootstrap::new,
                connected,
                channelInitializerSupplier,
                null,
                null
        );
    }

    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Supplier<Set<URI>> endpointsSupplier,
                             AtomicBoolean opened,
                             AtomicInteger nextEndpointPointer,
                             AtomicInteger nextRetryDelayPointer,
                             Supplier<Bootstrap> bootstrapSupplier,
                             Subject<Boolean> connected,
                             DrasylFunction<URI, ChannelInitializer<SocketChannel>, DrasylException> channelInitializerSupplier,
                             ChannelInitializer<SocketChannel> channelInitializer,
                             Channel channel) {
        this.retryDelays = retryDelays;
        this.workerGroup = workerGroup;
        this.endpointsSupplier = endpointsSupplier;
        this.opened = opened;
        this.nextEndpointPointer = nextEndpointPointer;
        this.nextRetryDelayPointer = nextRetryDelayPointer;
        this.bootstrapSupplier = bootstrapSupplier;
        this.connected = connected;
        this.channelInitializerSupplier = channelInitializerSupplier;
        this.channelInitializer = channelInitializer;
        this.channel = channel;
    }

    public void open() {
        if (opened.compareAndSet(false, true)) {
            connect(nextEndpoint());
        }
    }

    void connect(URI endpoint) {
        if (endpoint == null) {
            getLogger().debug("No endpoint present. Permanently unable to connect to Server.");
            failed();
            return;
        }

        getLogger().debug("Connect to Endpoint '{}'", endpoint);
        try {
            channelInitializer = channelInitializerSupplier.apply(endpoint);

            bootstrapSupplier.get().group(workerGroup).channel(NioSocketChannel.class).handler(channelInitializer).connect(endpoint.getHost(), webSocketPort(endpoint))
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
     * Returns the next peer's endpoint. Returns <code>null</code> if no endpoint is present.
     *
     * @return
     */
    URI nextEndpoint() {
        try {
            Set<URI> endpoints = endpointsSupplier.get();
            URI endpoint = SetUtil.nthElement(endpoints, nextEndpointPointer.get());
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
     * Returns <code>true</code> if the client should attempt to reconnect. Otherwise
     * <code>false</code> is returned.
     *
     * @return
     */
    protected boolean shouldRetry() {
        return opened.get() && !retryDelays.isEmpty();
    }

    /**
     * This method is called if a connection was not possible and no further connection attempts
     * will be made.
     */
    protected void failed() {
        // do nothing
    }

    /**
     * Returns the duration of delay before the client should make a new attempt to reconnect to
     * Server. Iterates over list of all delays specified in configuration. Uses last element
     * permanently when end of list is reached. If list is empty, a {@link IllegalArgumentException}
     * is thrown.
     *
     * @return
     */
    Duration nextRetryDelay() {
        Duration retryDelay = retryDelays.get(nextRetryDelayPointer.get());
        nextRetryDelayPointer.updateAndGet(p -> Math.min(p + 1, retryDelays.size() - 1));
        return retryDelay;
    }

    /**
     * Returns an observable which emits the value <code>true</code> if a connection with the Server
     * including handshake could be established. Otherwise <code>false</code> is returned.
     * <p>
     * The Observable immediately returns an item with the current state of the connection on a new
     * subscription.
     *
     * @return
     */
    public Observable<Boolean> connectionEstablished() {
        return connected.subscribeOn(DrasylScheduler.getInstanceLight());
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false) && channel != null) {
            // send quit message and close connections
            if (channel.isOpen()) {
                channel.writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN)).addListener(ChannelFutureListener.CLOSE);
            }

            channel.closeFuture().syncUninterruptibly();
            channel = null;
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