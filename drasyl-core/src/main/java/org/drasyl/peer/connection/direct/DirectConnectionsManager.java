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
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.event.Event;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.client.DirectClient;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerAdapter;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;

/**
 * This class is responsible for establishing and managing direct connections with other drasyl
 * nodes.
 */
@SuppressWarnings({ "java:S107" })
public class DirectConnectionsManager implements DrasylNodeComponent {
    static final String DIRECT_CONNECTIONS_MANAGER = "DIRECT_CONNECTIONS_MANAGER";
    private static final Logger LOG = LoggerFactory.getLogger(DirectConnectionsManager.class);
    private final DrasylConfig config;
    private final Identity identity;
    private final PeersManager peersManager;
    private final AtomicBoolean opened;
    private final Messenger messenger;
    private final DirectConnectionDemandsCache directConnectionDemandsCache;
    private final RequestPeerInformationCache requestPeerInformationCache;
    private final DrasylPipeline pipeline;
    private final PeerChannelGroup channelGroup;
    private final EventLoopGroup workerGroup;
    private final Consumer<Event> eventConsumer;
    private final Map<CompressedPublicKey, DirectClient> clients;
    private final BooleanSupplier acceptNewConnectionsSupplier;
    private final Set<URI> endpoints;
    private final int maxConnections;

    public DirectConnectionsManager(DrasylConfig config,
                                    Identity identity,
                                    PeersManager peersManager,
                                    Messenger messenger,
                                    DrasylPipeline pipeline,
                                    PeerChannelGroup channelGroup,
                                    EventLoopGroup workerGroup,
                                    Consumer<Event> eventConsumer,
                                    BooleanSupplier acceptNewConnectionsSupplier,
                                    Set<URI> nodeEndpoints,
                                    Observable<CompressedPublicKey> communicationOccurred) {
        this(
                config,
                identity,
                peersManager,
                new AtomicBoolean(false),
                messenger,
                pipeline,
                channelGroup,
                workerGroup,
                eventConsumer,
                nodeEndpoints,
                new DirectConnectionDemandsCache(config.getDirectConnectionsMaxConcurrentConnections(), ofSeconds(60)),
                new RequestPeerInformationCache(1_000, ofSeconds(60)),
                new HashMap<>(),
                acceptNewConnectionsSupplier,
                config.getDirectConnectionsMaxConcurrentConnections()
        );
        communicationOccurred.subscribe(this::communicationOccurred);
    }

    DirectConnectionsManager(DrasylConfig config,
                             Identity identity,
                             PeersManager peersManager,
                             AtomicBoolean opened,
                             Messenger messenger,
                             DrasylPipeline pipeline,
                             PeerChannelGroup channelGroup,
                             EventLoopGroup workerGroup,
                             Consumer<Event> eventConsumer,
                             Set<URI> nodeEndpoints,
                             DirectConnectionDemandsCache directConnectionDemandsCache,
                             RequestPeerInformationCache requestPeerInformationCache,
                             Map<CompressedPublicKey, DirectClient> clients,
                             BooleanSupplier acceptNewConnectionsSupplier,
                             int maxConnections) {
        this.config = config;
        this.identity = identity;
        this.peersManager = peersManager;
        this.opened = opened;
        this.messenger = messenger;
        this.channelGroup = channelGroup;
        this.workerGroup = workerGroup;
        this.eventConsumer = eventConsumer;
        this.endpoints = nodeEndpoints;
        this.directConnectionDemandsCache = directConnectionDemandsCache;
        this.requestPeerInformationCache = requestPeerInformationCache;
        this.pipeline = pipeline;
        this.clients = clients;
        this.acceptNewConnectionsSupplier = acceptNewConnectionsSupplier;
        this.maxConnections = maxConnections;
    }

    @Override
    public void open() {
        if (opened.compareAndSet(false, true)) {
            LOG.debug("Start Direct Connections Manager...");
            // add handler to the pipeline that listens for {@link PeerRelayEvent}s.
            pipeline.addLast(DIRECT_CONNECTIONS_MANAGER, new HandlerAdapter() {
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
            LOG.debug("Direct Connections Manager started.");
        }
    }

    /**
     * This method notifies the {@link DirectConnectionsManager} that a direct connection with
     * <code>publicKey</code> occurred.
     *
     * @param publicKey
     */
    void communicationOccurred(CompressedPublicKey publicKey) {
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

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            LOG.info("Stop Direct Connections Handler...");
            // remove handler that has been added in {@link #open()}
            pipeline.remove(DIRECT_CONNECTIONS_MANAGER);

            // close and remove all client connections
            for (Map.Entry<CompressedPublicKey, DirectClient> entry : new HashSet<>(clients.entrySet())) {
                CompressedPublicKey publicKey = entry.getKey();
                DirectClient client = entry.getValue();

                clients.remove(publicKey);
                client.close();
            }
            LOG.info("Direct Connections Handler stopped");
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
                messenger.send(new WhoisMessage(publicKey, identity.getPublicKey(), PeerInformation.of(endpoints)));
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
                if ((maxConnections == 0 || maxConnections > clients.size()) && !clients.containsKey(publicKey) && !endpointsSupplier.get().isEmpty()) {
                    DirectClient client = new DirectClient(
                            config,
                            identity,
                            peersManager,
                            messenger,
                            channelGroup,
                            workerGroup,
                            eventConsumer,
                            publicKey,
                            endpointsSupplier,
                            () -> directConnectionDemandsCache.contains(publicKey),
                            () -> clients.remove(publicKey),
                            acceptNewConnectionsSupplier);
                    LOG.debug("Initiate direct connection to Peer '{}'", publicKey);
                    clients.put(publicKey, client);
                    client.open();
                }
            }
        }
    }
}