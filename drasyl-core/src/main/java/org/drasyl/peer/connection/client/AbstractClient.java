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
import org.drasyl.crypto.Crypto;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.util.DrasylFunction;
import org.drasyl.util.DrasylScheduler;
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

public abstract class AbstractClient implements AutoCloseable {
    private final EventLoopGroup workerGroup;
    private final Set<URI> endpoints;
    private final AtomicBoolean opened;
    private final AtomicInteger nextEndpointPointer;
    private final AtomicInteger nextRetryDelayPointer;
    private final Supplier<Bootstrap> bootstrapSupplier;
    private final Subject<Boolean> connected;
    private final DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier;
    private final List<Duration> retryDelays;
    protected ChannelInitializer<SocketChannel> channelInitializer;
    protected Channel channel;

    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Set<URI> endpoints,
                             DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        this(
                retryDelays,
                workerGroup,
                endpoints,
                BehaviorSubject.createDefault(false),
                channelInitializerSupplier
        );
    }

    protected AbstractClient(List<Duration> retryDelays,
                             EventLoopGroup workerGroup,
                             Set<URI> endpoints,
                             Subject<Boolean> connected,
                             DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        this(
                retryDelays,
                workerGroup,
                endpoints,
                new AtomicBoolean(false),
                // The pointer should point to a random endpoint. This creates a distribution on different server's endpoints
                new AtomicInteger(endpoints.isEmpty() ? 0 : Crypto.randomNumber(endpoints.size())),
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
                             Set<URI> endpoints,
                             AtomicBoolean opened,
                             AtomicInteger nextEndpointPointer,
                             AtomicInteger nextRetryDelayPointer,
                             Supplier<Bootstrap> bootstrapSupplier,
                             Subject<Boolean> connected,
                             DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier,
                             ChannelInitializer<SocketChannel> channelInitializer,
                             Channel channel) {
        this.retryDelays = retryDelays;
        this.workerGroup = workerGroup;
        this.endpoints = endpoints;
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
        }
        catch (DrasylException e) {
            getLogger().warn("Unable to create channel initializer:", e);
            conditionalScheduledReconnect();
        }

        bootstrapSupplier.get().group(workerGroup).channel(NioSocketChannel.class).handler(channelInitializer).connect(endpoint.getHost(), webSocketPort(endpoint))
                .addListener((ChannelFutureListener) channelFuture -> {
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

    /**
     * Returns the next peer's endpoint. Returns <code>null</code> if no endpoint is present.
     *
     * @return
     */
    URI nextEndpoint() {
        URI[] myEndpoints = endpoints.toArray(new URI[0]);
        int index = nextEndpointPointer.get();
        if (myEndpoints.length > index) {
            nextEndpointPointer.updateAndGet(p -> (p + 1) % endpoints.size());
            return myEndpoints[index];
        }
        else {
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
    private boolean shouldRetry() {
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