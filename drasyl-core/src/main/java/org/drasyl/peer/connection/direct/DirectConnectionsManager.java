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
package org.drasyl.peer.connection.direct;

import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.client.DirectClient;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.InboundHandlerAdapter;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;

/**
 * This class is responsible for establishing and managing direct connections with other drasyl
 * nodes.
 */
@SuppressWarnings({ "java:S107" })
public class DirectConnectionsManager implements AutoCloseable {
    static final String DIRECT_CONNECTIONS_MANAGER = "DIRECT_CONNECTIONS_MANAGER";
    private static final Logger LOG = LoggerFactory.getLogger(DirectConnectionsManager.class);
    private final DrasylConfig config;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final AtomicBoolean opened;
    private final Messenger messenger;
    private final DirectConnectionDemandsCache directConnectionDemandsCache;
    private final RequestPeerInformationCache requestPeerInformationCache;
    private final DrasylPipeline pipeline;
    private final EventLoopGroup workerGroup;
    private final Consumer<Event> eventConsumer;
    private final ConcurrentMap<CompressedPublicKey, DirectClient> clients;
    private Set<URI> endpoints;

    public DirectConnectionsManager(DrasylConfig config,
                                    IdentityManager identityManager,
                                    PeersManager peersManager,
                                    Messenger messenger,
                                    DrasylPipeline pipeline,
                                    EventLoopGroup workerGroup,
                                    Consumer<Event> eventConsumer) {
        this(
                config,
                identityManager,
                peersManager,
                new AtomicBoolean(false),
                messenger,
                pipeline,
                workerGroup,
                eventConsumer,
                Set.of(),
                new DirectConnectionDemandsCache(config.getDirectConnectionsMaxConcurrentConnections(), ofSeconds(60)),
                new RequestPeerInformationCache(1_000, ofSeconds(60)),
                new ConcurrentHashMap<>()
        );
    }

    DirectConnectionsManager(DrasylConfig config,
                             IdentityManager identityManager,
                             PeersManager peersManager,
                             AtomicBoolean opened,
                             Messenger messenger,
                             DrasylPipeline pipeline,
                             EventLoopGroup workerGroup,
                             Consumer<Event> eventConsumer,
                             Set<URI> endpoints,
                             DirectConnectionDemandsCache directConnectionDemandsCache,
                             RequestPeerInformationCache requestPeerInformationCache,
                             ConcurrentMap<CompressedPublicKey, DirectClient> clients) {
        this.config = config;
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.opened = opened;
        this.messenger = messenger;
        this.workerGroup = workerGroup;
        this.eventConsumer = eventConsumer;
        this.endpoints = endpoints;
        this.directConnectionDemandsCache = directConnectionDemandsCache;
        this.requestPeerInformationCache = requestPeerInformationCache;
        this.pipeline = pipeline;
        this.clients = clients;
    }

    public void open() {
        if (opened.compareAndSet(false, true)) {
            // add handler to the pipeline that listens for {@link PeerRelayEvent}s.
            pipeline.addLast(DIRECT_CONNECTIONS_MANAGER, new InboundHandlerAdapter() {
                @Override
                public void eventTriggered(HandlerContext ctx, Event event) {
                    super.eventTriggered(ctx, event);

                    if (opened.get() && event instanceof PeerRelayEvent) {
                        PeerRelayEvent peerRelayEvent = (PeerRelayEvent) event;
                        CompressedPublicKey publicKey = peerRelayEvent.getPeer().getPublicKey();

                        initiateDirectConnectionOnDemand(publicKey);
                    }
                }
            });
        }
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            // remove handler that has been added in {@link #open()}
            pipeline.remove(DIRECT_CONNECTIONS_MANAGER);

            // close and remove all client connections
            for (Map.Entry<CompressedPublicKey, DirectClient> entry : clients.entrySet()) {
                CompressedPublicKey publicKey = entry.getKey();
                DirectClient client = entry.getValue();

                clients.remove(publicKey);
                client.close();
            }
        }
    }

    /**
     * Defines the endpoints that the local node sends to other peers when initializing direct
     * connections.
     *
     * @param endpoints
     */
    public void setEndpoints(Set<URI> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * This method notifies the {@link DirectConnectionsManager} that a direct connection with
     * <code>publicKey</code> occurred.
     *
     * @param publicKey
     */
    public void communicationOccurred(CompressedPublicKey publicKey) {
        if (opened.get()) {
            directConnectionDemandsCache.add(publicKey);
            Pair<PeerInformation, Set<Path>> peer = peersManager.getPeer(publicKey);
            Set<Path> paths = peer.second();
            if (paths.isEmpty()) {
                requestPeerInformation(publicKey);
            }
            else {
                initiateDirectConnectionOnDemand(publicKey);
            }
        }
    }

    /**
     * Requests information (e.g. endpoints) for the peer with the public key
     * <code>publicKey</code>. Ensures that the information is not requested too often.
     * Fails silently if the request could not be made.
     *
     * @param publicKey
     */
    private void requestPeerInformation(CompressedPublicKey publicKey) {
        if (requestPeerInformationCache.add(publicKey)) {
            LOG.debug("Request information for Peer '{}'", publicKey);
            try {
                messenger.send(new WhoisMessage(publicKey, identityManager.getPublicKey(), PeerInformation.of(endpoints)));
            }
            catch (MessengerException e) {
                LOG.debug("Unable to request information for Peer '{}': {}", publicKey, e.getMessage());
            }
        }
    }

    /**
     * Attempts to initiate a direct connection with the peer using the public key
     * <code>publicKey</code>. This method does nothing if there is no demand for a direct
     * connection to the peer (anymore). It is also ensured that no more direct connections than
     * specified in Config are created. Currently, the limited number of available direct connection
     * slots is being allocated by the "first come, first served" principle. Existing slots are only
     * released if a connection is terminated and there is no longer a demand for a direct
     * connection to the peer.
     *
     * @param publicKey
     */
    private void initiateDirectConnectionOnDemand(CompressedPublicKey publicKey) {
        if (directConnectionDemandsCache.contains(publicKey)) {
            Supplier<Set<URI>> endpointsSupplier = () -> {
                Pair<PeerInformation, Set<Path>> peer = peersManager.getPeer(publicKey);
                PeerInformation peerInformation = peer.first();
                return peerInformation.getEndpoints();
            };

            synchronized (this) {
                int maxConnections = config.getDirectConnectionsMaxConcurrentConnections();
                if (maxConnections == 0 || maxConnections > clients.size()) {
                    clients.computeIfAbsent(publicKey, myPublicKey -> {
                        DirectClient client = new DirectClient(
                                config,
                                identityManager::getIdentity,
                                peersManager,
                                messenger,
                                workerGroup,
                                eventConsumer,
                                this::communicationOccurred,
                                myPublicKey,
                                endpointsSupplier,
                                () -> directConnectionDemandsCache.contains(publicKey),
                                () -> clients.remove(publicKey)
                        );
                        LOG.debug("Initiate direct connection to Peer '{}'", publicKey);
                        client.open();
                        return client;
                    });
                }
            }
        }
    }
}