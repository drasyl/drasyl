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
package org.drasyl;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.Beta;
import com.typesafe.config.ConfigException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroupFutureListener;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.sentry.Sentry;
import io.sentry.event.User;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.MessageSinkException;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.client.SuperPeerClient;
import org.drasyl.peer.connection.direct.DirectConnectionsManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.IdentityMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RelayableMessage;
import org.drasyl.peer.connection.message.WhoisMessage;
import org.drasyl.peer.connection.server.Server;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugins.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.util.DrasylScheduler.getInstanceHeavy;

/**
 * Represents a node in the drasyl Overlay Network. Applications that want to run on drasyl must
 * implement this class.
 * <p>
 * Example usage:
 * <pre><code>
 * DrasylNode node = new DrasylNode() {
 *   &#64;Override
 *   public void onEvent(Event event) {
 *     // handle incoming events (messages) here
 *     System.out.println("Event received: " + event);
 *   }
 * };
 * node.start();
 *
 * // wait till NodeOnlineEvent has been received
 *
 * // send message to another node
 * node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World");
 *
 * // shutdown node
 * node.shutdown();
 * </code></pre>
 */
@SuppressWarnings({ "java:S107" })
@Beta
public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);
    private static final List<DrasylNode> INSTANCES;
    private static final EventLoopGroup WORKER_GROUP;
    private static final EventLoopGroup BOSS_GROUP;

    static {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        Sentry.getStoredClient().setRelease(DrasylNode.getVersion());
        INSTANCES = Collections.synchronizedList(new ArrayList<>());
        // https://github.com/netty/netty/issues/639#issuecomment-9263566
        WORKER_GROUP = new NioEventLoopGroup(Math.min(2, Math.max(2, Runtime.getRuntime().availableProcessors() * 2 / 3 - 2)));
        BOSS_GROUP = new NioEventLoopGroup(2);
    }

    private final DrasylConfig config;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final PeerChannelGroup channelGroup;
    private final Messenger messenger;
    private final Set<URI> endpoints;
    private final AtomicBoolean acceptNewConnections;
    private final DrasylPipeline pipeline;
    private final List<DrasylNodeComponent> components;
    private final AtomicBoolean started;
    private CompletableFuture<Void> startSequence;
    private CompletableFuture<Void> shutdownSequence;

    /**
     * Creates a new drasyl Node.
     */
    public DrasylNode() throws DrasylException {
        this(new DrasylConfig());
    }

    /**
     * Creates a new drasyl Node with the given <code>config</code>.
     *
     * @param config
     */
    @SuppressWarnings({ "java:S2095" })
    public DrasylNode(DrasylConfig config) throws DrasylException {
        try {
            this.config = config;
            this.identityManager = new IdentityManager(this.config);
            this.peersManager = new PeersManager(this::onInternalEvent);
            this.channelGroup = new PeerChannelGroup();
            this.messenger = new Messenger(this::messageSink, peersManager, channelGroup);
            this.endpoints = new HashSet<>();
            this.acceptNewConnections = new AtomicBoolean();
            this.pipeline = new DrasylPipeline(this::onEvent, messenger::send, config);
            this.components = new ArrayList<>();
            this.components.add(new PluginManager(pipeline, config));
            Consumer<CompressedPublicKey> communicationOccurredConsumer;
            if (config.areDirectConnectionsEnabled()) {
                DirectConnectionsManager directConnectionsManager = new DirectConnectionsManager(config, identityManager, peersManager, messenger, pipeline, channelGroup, DrasylNode.WORKER_GROUP, this::onInternalEvent, acceptNewConnections::get, endpoints);
                communicationOccurredConsumer = directConnectionsManager::communicationOccurred;
                this.components.add(directConnectionsManager);
            }
            else {
                communicationOccurredConsumer = publicKey -> {
                };
            }
            if (config.isIntraVmDiscoveryEnabled()) {
                IntraVmDiscovery intraVmDiscovery = new IntraVmDiscovery(identityManager::getPublicKey, messenger, peersManager, this::onInternalEvent);
                this.components.add(intraVmDiscovery);
            }
            Observable<Boolean> superPeerConnected;
            if (config.isSuperPeerEnabled()) {
                SuperPeerClient superPeerClient = new SuperPeerClient(this.config, identityManager::getIdentity, peersManager, messenger, channelGroup, DrasylNode.WORKER_GROUP, this::onInternalEvent, communicationOccurredConsumer, acceptNewConnections::get);
                superPeerConnected = superPeerClient.connectionEstablished();
                this.components.add(superPeerClient);
            }
            else {
                superPeerConnected = BehaviorSubject.createDefault(false);
            }
            if (config.isServerEnabled()) {
                Server server = new Server(identityManager::getIdentity, messenger, peersManager, this.config, channelGroup, DrasylNode.WORKER_GROUP, DrasylNode.BOSS_GROUP, superPeerConnected, communicationOccurredConsumer, endpoints, acceptNewConnections::get);
                this.components.add(server);
            }
            if (config.isMonitoringEnabled()) {
                this.components.add(new Monitoring(config, peersManager, identityManager::getPublicKey, pipeline));
            }
            this.started = new AtomicBoolean();
            this.startSequence = new CompletableFuture<>();
            this.shutdownSequence = completedFuture(null);
            setLogLevel(this.config.getLoglevel());
        }
        catch (ConfigException e) {
            throw new DrasylException("Couldn't load config: \n" + e.getMessage());
        }
    }

    /**
     * Sends <code>event</code> to the {@link Pipeline} and tells it information about the local
     * node, other peers, connections or incoming messages.
     * <br>
     * <b>This method should be used by all drasyl internal operations, so that the event can pass
     * the {@link org.drasyl.pipeline.Pipeline}</b>
     *
     * @param event the event
     */
    void onInternalEvent(Event event) {
        pipeline.processInbound(event);
    }

    void messageSink(RelayableMessage message) throws MessageSinkException {
        CompressedPublicKey recipient = message.getRecipient();

        if (!identityManager.getPublicKey().equals(recipient)) {
            throw new NoPathToIdentityException(recipient);
        }

        if (message instanceof ApplicationMessage) {
            ApplicationMessage applicationMessage = (ApplicationMessage) message;
            peersManager.addPeer(applicationMessage.getSender());
            pipeline.processInbound(applicationMessage);
        }
        else if (message instanceof WhoisMessage) {
            WhoisMessage whoisMessage = (WhoisMessage) message;
            peersManager.setPeerInformation(whoisMessage.getRequester(), whoisMessage.getPeerInformation());

            CompressedPublicKey myPublicKey = identityManager.getPublicKey();
            PeerInformation myPeerInformation = PeerInformation.of(endpoints);
            IdentityMessage identityMessage = new IdentityMessage(whoisMessage.getRequester(), myPublicKey, myPeerInformation, whoisMessage.getId());

            try {
                messenger.send(identityMessage);
            }
            catch (MessengerException e) {
                LOG.info("Unable to reply to {}: {}", whoisMessage, e.getMessage());
            }
        }
        else if (message instanceof IdentityMessage) {
            IdentityMessage identityMessage = (IdentityMessage) message;
            peersManager.setPeerInformation(identityMessage.getPublicKey(), identityMessage.getPeerInformation());
        }
        else {
            throw new IllegalArgumentException("DrasylNode.loopbackMessageSink is not able to handle messages of type " + message.getClass().getSimpleName());
        }
    }

    /**
     * Sends <code>event</code> to the application and tells it information about the local node,
     * other peers, connections or incoming messages.
     *
     * @param event the event
     */
    public abstract void onEvent(Event event);

    /**
     * @return return log level of loggers in org.drasyl package namespace
     */
    public static Level getLogLevel() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
        return root.getLevel();
    }

    /**
     * Set log level of all drasyl loggers in org.drasyl package namespace.
     *
     * @param level
     */
    public static void setLogLevel(Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
        root.setLevel(level);
    }

    DrasylNode(DrasylConfig config,
               IdentityManager identityManager,
               PeersManager peersManager,
               PeerChannelGroup channelGroup,
               Messenger messenger,
               Set<URI> endpoints,
               AtomicBoolean acceptNewConnections,
               DrasylPipeline pipeline,
               List<DrasylNodeComponent> components,
               AtomicBoolean started,
               CompletableFuture<Void> startSequence,
               CompletableFuture<Void> shutdownSequence) {
        this.config = config;
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.messenger = messenger;
        this.endpoints = endpoints;
        this.acceptNewConnections = acceptNewConnections;
        this.pipeline = pipeline;
        this.channelGroup = channelGroup;
        this.components = components;
        this.started = started;
        this.startSequence = startSequence;
        this.shutdownSequence = shutdownSequence;
    }

    public CompletableFuture<Void> send(String recipient, byte[] payload) throws DrasylException {
        try {
            return send(CompressedPublicKey.of(recipient), payload);
        }
        catch (CryptoException | IllegalArgumentException e) {
            throw new DrasylException("Unable to parse recipient's public key: " + e.getMessage());
        }
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link MessengerException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(CompressedPublicKey recipient,
                                        byte[] payload) {
        return pipeline.processOutbound(new ApplicationMessage(identityManager.getPublicKey(), recipient, payload));
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link MessengerException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @throws MessengerException if an error occurs during the processing
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(String recipient, String payload) throws DrasylException {
        return send(recipient, payload.getBytes());
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link MessengerException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(CompressedPublicKey recipient,
                                        String payload) {
        return send(recipient, payload.getBytes());
    }

    /**
     * Shut the drasyl node down.
     * <p>
     * If there is a connection to a Super Peer, our node will deregister from that Super Peer.
     * <p>
     * If the local server has been started, it will now be stopped.
     * <p>
     * This method does not stop the shared threads. To kill the shared threads, you have to call
     * the {@link #irrevocablyTerminate()} method.
     * <p>
     *
     * @return this method returns a future, which complements if all shutdown steps have been
     * completed.
     */
    public CompletableFuture<Void> shutdown() {
        if (started.compareAndSet(true, false)) {
            DrasylNode self = this;
            onInternalEvent(new NodeDownEvent(Node.of(identityManager.getIdentity(), endpoints)));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identityManager.getIdentity());
            shutdownSequence = new CompletableFuture<>();
            getInstanceHeavy().scheduleDirect(() -> {
                rejectNewConnections();
                closeConnections();
                for (int i = components.size() - 1; i >= 0; i--) {
                    components.get(i).close();
                }

                onInternalEvent(new NodeNormalTerminationEvent(Node.of(identityManager.getIdentity(), endpoints)));
                LOG.info("drasyl Node with Identity '{}' has shut down", identityManager.getIdentity());
                shutdownSequence.complete(null);
                INSTANCES.remove(self);
            });
        }

        return shutdownSequence;
    }

    private void rejectNewConnections() {
        acceptNewConnections.set(false);
    }

    /**
     * Start the drasyl node.
     * <p>
     * First, the identity of the node is loaded. If none exists, a new one is generated.
     * <p>
     * If activated, a local server is started. This allows other nodes to discover our node.
     * <p>
     * If a super peer has been configured, a client is started which connects to this super peer.
     * Our node uses the Super Peer to discover and communicate with other nodes.
     * <p>
     *
     * @return this method returns a future, which complements if all components necessary for the
     * operation have been started.
     */
    public CompletableFuture<Void> start() {
        if (started.compareAndSet(false, true)) {
            INSTANCES.add(this);
            LOG.info("Start drasyl Node v{}...", DrasylNode.getVersion());
            LOG.debug("The following configuration will be used: {}", config);
            startSequence = new CompletableFuture<>();
            getInstanceHeavy().scheduleDirect(() -> {
                try {
                    loadIdentity();
                    for (int i = 0; i < components.size(); i++) {
                        components.get(i).open();
                    }
                    acceptNewConnections();

                    onInternalEvent(new NodeUpEvent(Node.of(identityManager.getIdentity(), endpoints)));
                    LOG.info("drasyl Node with Identity '{}' has started", identityManager.getIdentity());
                    startSequence.complete(null);
                }
                catch (DrasylException e) {
                    onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identityManager.getIdentity(), endpoints), e));
                    LOG.info("Could not start drasyl Node: {}", e.getMessage());
                    LOG.info("Stop all running components...");

                    rejectNewConnections();
                    closeConnections();
                    for (int i = components.size() - 1; i >= 0; i--) {
                        components.get(i).close();
                    }

                    LOG.info("All components stopped");
                    started.set(false);
                    startSequence.completeExceptionally(e);
                }
            });
        }

        return startSequence;
    }

    @SuppressWarnings({ "java:S1905" })
    private void closeConnections() {
        // send quit message to all peers and close connections
        channelGroup.writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN))
                .addListener((ChannelGroupFutureListener) future -> future.group().close());
    }

    private void acceptNewConnections() {
        acceptNewConnections.set(true);
    }

    /**
     * Returns the version of the node. If the version could not be read, <code>null</code> is
     * returned.
     */
    public static String getVersion() {
        final Properties properties = new Properties();
        try {
            properties.load(DrasylNode.class.getClassLoader().getResourceAsStream("project.properties"));
            return properties.getProperty("version");
        }
        catch (IOException e) {
            return null;
        }
    }

    private void loadIdentity() throws IdentityManagerException {
        identityManager.loadOrCreateIdentity();
        LOG.debug("Using Identity '{}'", identityManager.getIdentity());
        Sentry.getContext().setUser(new User(identityManager.getPublicKey().toString(), null, null, null));
    }

    /**
     * Returns the {@link Pipeline} to allow users to add own handlers.
     *
     * @return the pipeline
     */
    public Pipeline pipeline() {
        return this.pipeline;
    }

    /**
     * This method stops the shared threads ({@link EventLoopGroup}s), but only if none {@link
     * DrasylNode} is using them anymore.
     *
     * <p>
     * <b>This operation cannot be undone. After performing this operation, no new DrasylNodes can
     * be created!</b>
     * </p>
     */
    public static void irrevocablyTerminate() {
        if (INSTANCES.isEmpty()) {
            BOSS_GROUP.shutdownGracefully().syncUninterruptibly();
            WORKER_GROUP.shutdownGracefully().syncUninterruptibly();
        }
    }
}
