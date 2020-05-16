package org.drasyl.peer.connection.superpeer;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.event.EventCode;
import org.drasyl.event.Node;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.JoinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;

/**
 * This class represents the link between <code>DrasylNode</code> and the super peer. It is
 * responsible for maintaining the connection to the super peer and updates the data of the super
 * peer in <code>PeersManager</code>.
 */
@SuppressWarnings({ "java:S107", "java:S4818" })
public class SuperPeerClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClient.class);
    private final DrasylNodeConfig config;
    private final EventLoopGroup workerGroup;
    private final IdentityManager identityManager;
    private final Messenger messenger;
    private final PeersManager peersManager;
    private final Set<URI> endpoints;
    private final AtomicBoolean opened;
    private final AtomicInteger nextEndpointPointer;
    private final AtomicInteger nextRetryDelayPointer;
    private final Consumer<Event> onEvent;
    private Channel clientChannel;

    SuperPeerClient(DrasylNodeConfig config,
                    IdentityManager identityManager,
                    PeersManager peersManager,
                    Messenger messenger,
                    EventLoopGroup workerGroup,
                    Set<URI> endpoints,
                    AtomicBoolean opened,
                    AtomicInteger nextEndpointPointer,
                    AtomicInteger nextRetryDelayPointer,
                    Consumer<Event> onEvent) {
        this.identityManager = identityManager;
        this.messenger = messenger;
        this.peersManager = peersManager;
        this.config = config;
        this.workerGroup = workerGroup;
        this.endpoints = endpoints;
        this.opened = opened;
        this.nextEndpointPointer = nextEndpointPointer;
        this.nextRetryDelayPointer = nextRetryDelayPointer;
        this.onEvent = onEvent;
    }

    public SuperPeerClient(DrasylNodeConfig config,
                           IdentityManager identityManager,
                           PeersManager peersManager,
                           Messenger messenger,
                           EventLoopGroup workerGroup,
                           Consumer<Event> onEvent) throws SuperPeerClientException {
        try {
            endpoints = new HashSet<>();
            for (String endpoint : config.getSuperPeerEndpoints()) {
                endpoints.add(new URI(endpoint));
            }

            if (endpoints.isEmpty()) {
                throw new SuperPeerClientException("At least one Super Peer Endpoint must be specified.");
            }

            this.identityManager = identityManager;
            this.messenger = messenger;
            this.peersManager = peersManager;
            this.config = config;
            this.workerGroup = workerGroup;
            this.opened = new AtomicBoolean(false);
            // The pointer should point to a random endpoint. This creates a distribution on different super peer's endpoints
            this.nextEndpointPointer = new AtomicInteger(endpoints.isEmpty() ? 0 : Crypto.randomNumber(endpoints.size()));
            this.nextRetryDelayPointer = new AtomicInteger(0);
            this.onEvent = onEvent;
        }
        catch (URISyntaxException e) {
            throw new SuperPeerClientException("Unable to parse super peer endpoints: " + e.getMessage());
        }
    }

    public void open(Set<URI> entryPoints) {
        if (opened.compareAndSet(false, true)) {
            new Thread(() -> keepConnectionAlive(entryPoints)).start();
        }
    }

    private void keepConnectionAlive(Set<URI> entryPoints) {
        do {
            URI endpoint = getEndpoint();
            LOG.debug("Connect to Super Peer Endpoint '{}'", endpoint);
            try {
                SuperPeerClientBootstrap clientBootstrap = new SuperPeerClientBootstrap(config, workerGroup, endpoint, this);
                clientChannel = clientBootstrap.getChannel();
                clientChannel.writeAndFlush(new JoinMessage(identityManager.getKeyPair().getPublicKey(), entryPoints))
                        .syncUninterruptibly();
                clientChannel.closeFuture().syncUninterruptibly();
                onEvent.accept(new Event(EventCode.EVENT_NODE_OFFLINE, Node.of(identityManager.getIdentity())));

                Duration duration = retryDelay();
                if (!duration.isZero()) {
                    LOG.debug("Wait {} ms before retry reconnect to Super Peer", duration.toMillis());
                    sleep(duration.toMillis());
                }
            }
            catch (SuperPeerClientException e) {
                LOG.warn("Error while trying to connect to super peer: {}", e.getMessage());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (IllegalStateException e) {
                LOG.debug("Working Group has rejected the new bootstrap. Maybe the node is shutting down.");
                Thread.currentThread().interrupt();
                break;
            }
        } while (retryConnection() && doRetryCycle()); //NOSONAR
    }

    /**
     * Returns the next Super Peer's endpoint. Iterates over list of all endpoints specified in
     * configuration. Jumps back to start when end of list is reached.
     *
     * @return
     */
    private URI getEndpoint() {
        URI[] myEndpoints = endpoints.toArray(new URI[0]);
        return myEndpoints[nextEndpointPointer.get()];
    }

    /**
     * Returns the duration of delay before the client should make a new attempt to reconnect to
     * Super Peer. Iterates over list of all delays specified in configuration. Uses last element
     * permanently when end of list is reached. If list is empty, a {@link IllegalArgumentException}
     * is thrown.
     *
     * @return
     */
    private Duration retryDelay() {
        Duration[] delays = config.getSuperPeerRetryDelays().toArray(new Duration[0]);
        if (delays.length == 0) {
            throw new IllegalArgumentException("No Retry Delays given!");
        }

        return delays[nextRetryDelayPointer.get()];
    }

    /**
     * Returns <code>true</code> if the client should try to reconnect to super peer. Otherwise
     * <code>false</code> is returned.
     *
     * @return
     */
    private boolean retryConnection() {
        return opened.get();
    }

    /**
     * Increases the internal counters for retries. Ensures that the client iterates over the
     * available Super Peer endpoints and throttles the speed of attempts to reconnect. Always
     * returns <code>true</code>.
     *
     * @return
     */
    private boolean doRetryCycle() {
        nextEndpointPointer.updateAndGet(p -> (p + 1) % endpoints.size());
        List<Duration> delays = config.getSuperPeerRetryDelays();
        nextRetryDelayPointer.updateAndGet(p -> Math.min(p, delays.size()));
        return true;
    }

    public IdentityManager getIdentityManager() {
        return identityManager;
    }

    public Messenger getMessenger() {
        return messenger;
    }

    public PeersManager getPeersManager() {
        return peersManager;
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false) && clientChannel != null && clientChannel.isOpen()) {
            clientChannel.close().syncUninterruptibly();
        }
    }

    public Consumer<Event> getOnEvent() {
        return onEvent;
    }
}
