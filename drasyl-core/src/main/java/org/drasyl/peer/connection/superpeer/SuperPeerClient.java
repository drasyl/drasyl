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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.ConnectionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Thread.sleep;
import static org.drasyl.event.EventType.EVENT_NODE_OFFLINE;
import static org.drasyl.event.EventType.EVENT_NODE_ONLINE;
import static org.drasyl.peer.connection.PeerConnection.CloseReason.REASON_SHUTTING_DOWN;

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
    private final ConnectionsManager connectionsManager;
    private final Set<URI> endpoints;
    private final AtomicBoolean opened;
    private final AtomicInteger nextEndpointPointer;
    private final AtomicInteger nextRetryDelayPointer;
    private final Consumer<Event> eventConsumer;
    private final Function<Set<URI>, Thread> threadSupplier;
    private Channel clientChannel;

    SuperPeerClient(DrasylNodeConfig config,
                    IdentityManager identityManager,
                    PeersManager peersManager,
                    Messenger messenger,
                    EventLoopGroup workerGroup,
                    ConnectionsManager connectionsManager,
                    Set<URI> endpoints,
                    AtomicBoolean opened,
                    AtomicInteger nextEndpointPointer,
                    AtomicInteger nextRetryDelayPointer,
                    Consumer<Event> eventConsumer,
                    Channel clientChannel,
                    Function<Set<URI>, Thread> threadSupplier) {
        this.identityManager = identityManager;
        this.messenger = messenger;
        this.peersManager = peersManager;
        this.config = config;
        this.workerGroup = workerGroup;
        this.connectionsManager = connectionsManager;
        this.endpoints = endpoints;
        this.opened = opened;
        this.nextEndpointPointer = nextEndpointPointer;
        this.nextRetryDelayPointer = nextRetryDelayPointer;
        this.eventConsumer = eventConsumer;
        this.clientChannel = clientChannel;
        this.threadSupplier = threadSupplier;
    }

    public SuperPeerClient(DrasylNodeConfig config,
                           IdentityManager identityManager,
                           PeersManager peersManager,
                           Messenger messenger,
                           EventLoopGroup workerGroup,
                           ConnectionsManager connectionsManager,
                           Consumer<Event> eventConsumer) throws SuperPeerClientException {
        endpoints = config.getSuperPeerEndpoints();

        if (endpoints.isEmpty()) {
            throw new SuperPeerClientException("At least one Super Peer Endpoint must be specified.");
        }

        this.identityManager = identityManager;
        this.messenger = messenger;
        this.peersManager = peersManager;
        this.connectionsManager = connectionsManager;
        this.config = config;
        this.workerGroup = workerGroup;
        this.opened = new AtomicBoolean(false);
        // The pointer should point to a random endpoint. This creates a distribution on different super peer's endpoints
        this.nextEndpointPointer = new AtomicInteger(endpoints.isEmpty() ? 0 : Crypto.randomNumber(endpoints.size()));
        this.nextRetryDelayPointer = new AtomicInteger(0);
        this.eventConsumer = eventConsumer;
        this.threadSupplier = myEntryPoints -> new Thread(() -> keepConnectionAlive(myEntryPoints));
    }

    void keepConnectionAlive(Set<URI> entryPoints) {
        do {
            URI endpoint = getEndpoint();
            LOG.debug("Connect to Super Peer Endpoint '{}'", endpoint);
            try {
                SuperPeerClientChannelBootstrap clientBootstrap = new SuperPeerClientChannelBootstrap(config, workerGroup, endpoint, entryPoints, this);
                clientChannel = clientBootstrap.getChannel();
                eventConsumer.accept(new Event(EVENT_NODE_ONLINE, Node.of(identityManager.getAddress())));
                clientChannel.closeFuture().syncUninterruptibly();
                eventConsumer.accept(new Event(EVENT_NODE_OFFLINE, Node.of(identityManager.getAddress())));
            }
            catch (SuperPeerClientException e) {
                LOG.warn("Error while trying to connect to Super Peer: {}", e.getMessage());
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
     * This message blocks until the client should make another connect attempt and then returns
     * <code>true</code. Otherwise <code>false</code> is returned.
     *
     * @return
     */
    boolean retryConnection() {
        if (opened.get() && !config.getSuperPeerRetryDelays().isEmpty()) {
            try {
                Duration duration = retryDelay();
                LOG.debug("Wait {}ms before retry connect to Super Peer", duration.toMillis());
                sleep(duration.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Increases the internal counters for retries. Ensures that the client iterates over the
     * available Super Peer endpoints and throttles the speed of attempts to reconnect. Always
     * returns <code>true</code>.
     *
     * @return
     */
    boolean doRetryCycle() {
        nextEndpointPointer.updateAndGet(p -> (p + 1) % endpoints.size());
        List<Duration> delays = config.getSuperPeerRetryDelays();
        nextRetryDelayPointer.updateAndGet(p -> Math.min(p + 1, delays.size() - 1));
        return true;
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
        return config.getSuperPeerRetryDelays().get(nextRetryDelayPointer.get());
    }

    public void open(Set<URI> entryPoints) {
        if (opened.compareAndSet(false, true)) {
            threadSupplier.apply(entryPoints).start();
        }
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
        if (opened.compareAndSet(true, false)) {
            connectionsManager.closeConnectionsOfType(SuperPeerClientConnection.class, REASON_SHUTTING_DOWN);

            if (clientChannel != null && clientChannel.isOpen()) {
                clientChannel.close().syncUninterruptibly();
            }
        }
    }

    public ConnectionsManager getConnectionsManager() {
        return connectionsManager;
    }
}
