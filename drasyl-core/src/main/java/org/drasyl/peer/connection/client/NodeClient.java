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

public abstract class NodeClient implements AutoCloseable {
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

    protected NodeClient(List<Duration> retryDelays,
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

    protected NodeClient(List<Duration> retryDelays,
                         EventLoopGroup workerGroup,
                         Set<URI> endpoints,
                         Subject<Boolean> connected,
                         DrasylFunction<URI, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        this(
                retryDelays,
                workerGroup,
                endpoints,
                new AtomicBoolean(false),
                // The pointer should point to a random endpoint. This creates a distribution on different super peer's endpoints
                new AtomicInteger(endpoints.isEmpty() ? 0 : Crypto.randomNumber(endpoints.size())),
                new AtomicInteger(0),
                Bootstrap::new,
                connected,
                channelInitializerSupplier,
                null,
                null
        );
    }

    protected NodeClient(List<Duration> retryDelays,
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
            connect();
        }
    }

    void connect() {
        URI endpoint = getCurrentEndpoint();
        getLogger().debug("Connect to Super Peer Endpoint '{}'", endpoint);
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
                        getLogger().warn("Error while trying to connect to Endpoint '{}':", endpoint, channelFuture.cause());
                        conditionalScheduledReconnect();
                    }
                });
    }

    /**
     * Returns the current peer's endpoint. Iterates over list of all endpoints specified in {@link
     * #endpoints}. Jumps back to start when end of list is reached.
     *
     * @return
     */
    private URI getCurrentEndpoint() {
        URI[] myEndpoints = endpoints.toArray(new URI[0]);
        return myEndpoints[nextEndpointPointer.get()];
    }

    /**
     * If {@link #shouldRetry()} returns <code>true</code>, an attempt to (re)connect to the Super
     * Peer is scheduled.
     */
    void conditionalScheduledReconnect() {
        if (shouldRetry()) {
            doRetryCycle();
            Duration duration = retryDelay();
            getLogger().debug("Wait {}ms before reconnect", duration.toMillis());
            workerGroup.schedule(() -> {
                if (opened.get()) {
                    connect();
                }
            }, duration.toMillis(), MILLISECONDS);
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
     * Returns the duration of delay before the client should make a new attempt to reconnect to
     * Super Peer. Iterates over list of all delays specified in configuration. Uses last element
     * permanently when end of list is reached. If list is empty, a {@link IllegalArgumentException}
     * is thrown.
     *
     * @return
     */
    Duration retryDelay() {
        return retryDelays.get(nextRetryDelayPointer.get());
    }

    /**
     * Returns an observable which emits the value <code>true</code> if a connection with the super
     * peer including handshake could be established. Otherwise <code>false</code> is returned.
     * <p>
     * The Observable immediately returns an item with the current state of the connection on a new
     * subscription.
     *
     * @return
     */
    public Observable<Boolean> connectionEstablished() {
        return connected.subscribeOn(DrasylScheduler.getInstance());
    }

    /**
     * Increases the internal counters for retries. Ensures that the client iterates over the
     * available Super Peer endpoints and throttles the speed of attempts to reconnect.
     *
     * @return
     */
    protected void doRetryCycle() {
        nextEndpointPointer.updateAndGet(p -> (p + 1) % endpoints.size());
        nextRetryDelayPointer.updateAndGet(p -> Math.min(p + 1, retryDelays.size() - 1));
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

    protected abstract Logger getLogger();
}