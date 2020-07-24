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
import io.sentry.Sentry;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.messenger.MessageSinkException;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.messenger.NoPathToPublicKeyException;
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
import org.drasyl.pipeline.codec.Codec;
import org.drasyl.plugins.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
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
    private final Identity identity;
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
     * Creates a new drasyl Node. The node is only being created, it neither connects to the Overlay
     * Network, nor can send or receive messages. To do this you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     */
    public DrasylNode() throws DrasylException {
        this(new DrasylConfig());
    }

    /**
     * Creates a new drasyl Node with the given <code>config</code>. The node is only being created,
     * it neither connects to the Overlay * Network, nor can send or receive messages. To do this
     * you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     *
     * @param config custom configuration used for this node
     */
    @SuppressWarnings({ "java:S2095" })
    public DrasylNode(DrasylConfig config) throws DrasylException {
        try {
            this.config = config;
            IdentityManager identityManager = new IdentityManager(this.config);
            identityManager.loadOrCreateIdentity();
            this.identity = identityManager.getIdentity();
            this.peersManager = new PeersManager(this::onInternalEvent);
            this.channelGroup = new PeerChannelGroup();
            this.messenger = new Messenger(this::messageSink, peersManager, channelGroup);
            this.endpoints = new CopyOnWriteArraySet<>();
            this.acceptNewConnections = new AtomicBoolean();
            this.pipeline = new DrasylPipeline(this::onEvent, messenger::send, config, identity);
            this.components = new ArrayList<>();
            this.components.add(new PluginManager(pipeline, config));
            if (config.areDirectConnectionsEnabled()) {
                this.components.add(new DirectConnectionsManager(config, identity, peersManager, messenger, pipeline, channelGroup, DrasylNode.WORKER_GROUP, this::onInternalEvent, acceptNewConnections::get, endpoints, messenger.communicationOccurred()));
            }
            if (config.isIntraVmDiscoveryEnabled()) {
                this.components.add(new IntraVmDiscovery(identity.getPublicKey(), messenger, peersManager, this::onInternalEvent));
            }
            if (config.isSuperPeerEnabled()) {
                this.components.add(new SuperPeerClient(this.config, identity, peersManager, messenger, channelGroup, DrasylNode.WORKER_GROUP, this::onInternalEvent, acceptNewConnections::get));
            }
            if (config.isServerEnabled()) {
                this.components.add(new Server(identity, messenger, peersManager, this.config, channelGroup, DrasylNode.WORKER_GROUP, DrasylNode.BOSS_GROUP, endpoints, acceptNewConnections::get));
            }
            if (config.isMonitoringEnabled()) {
                this.components.add(new Monitoring(config, peersManager, identity.getPublicKey(), pipeline));
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

    CompletableFuture<Void> messageSink(RelayableMessage message) throws MessageSinkException {
        CompressedPublicKey recipient = message.getRecipient();

        if (!identity.getPublicKey().equals(recipient)) {
            throw new NoPathToPublicKeyException(recipient);
        }

        if (message instanceof ApplicationMessage) {
            ApplicationMessage applicationMessage = (ApplicationMessage) message;
            peersManager.addPeer(applicationMessage.getSender());
            pipeline.processInbound(applicationMessage);
            // TODO: use future returned by pipeline.processInbound(applicationMessage)
            return completedFuture(null);
        }
        else if (message instanceof WhoisMessage) {
            WhoisMessage whoisMessage = (WhoisMessage) message;
            peersManager.setPeerInformation(whoisMessage.getRequester(), whoisMessage.getPeerInformation());

            CompressedPublicKey myPublicKey = identity.getPublicKey();
            PeerInformation myPeerInformation = PeerInformation.of(endpoints);
            IdentityMessage identityMessage = new IdentityMessage(whoisMessage.getRequester(), myPublicKey, myPeerInformation, whoisMessage.getId());

            try {
                return messenger.send(identityMessage);
            }
            catch (MessengerException e) {
                LOG.info("Unable to reply to {}: {}", whoisMessage, e.getMessage());
                return completedFuture(null);
            }
        }
        else if (message instanceof IdentityMessage) {
            IdentityMessage identityMessage = (IdentityMessage) message;
            peersManager.setPeerInformation(identityMessage.getPublicKey(), identityMessage.getPeerInformation());
            return completedFuture(null);
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
     * Return log level of loggers in org.drasyl package namespace.
     *
     * @return return log level of loggers in org.drasyl package namespace
     */
    public static Level getLogLevel() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
        return root.getLevel();
    }

    /**
     * Set log level of all drasyl loggers in org.drasyl package namespace.
     *
     * @param level new log level
     */
    @SuppressWarnings({ "java:S4792" })
    public static void setLogLevel(Level level) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl");
        root.setLevel(level);
    }

    DrasylNode(DrasylConfig config,
               Identity identity,
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
        this.identity = identity;
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

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link MessengerException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. By default, drasyl allows the
     * serialization of Java's primitive types, as well as {@link String}, {@link Collections},
     * {@link Map}, {@link Number}, {@link URI} and all classes in the {@code org.drasyl} package.
     * Further objects can be added on start via the {@link DrasylConfig} or on demand via {@link
     * org.drasyl.pipeline.HandlerContext#validator}. If the {@link org.drasyl.pipeline.codec.DefaultCodec}
     * does not support these objects, a custom {@link Codec} can be added to the beginning of the
     * {@link Pipeline}.
     * </p>
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @see org.drasyl.pipeline.codec.Codec
     * @see org.drasyl.pipeline.codec.DefaultCodec
     * @see org.drasyl.pipeline.codec.TypeValidator
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(String recipient, Object payload) {
        try {
            return send(CompressedPublicKey.of(recipient), payload);
        }
        catch (CryptoException | IllegalArgumentException e) {
            return failedFuture(new DrasylException("Unable to parse recipient's public key: " + e.getMessage()));
        }
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link MessengerException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. By default, drasyl allows the
     * serialization of Java's primitive types, as well as {@link String}, {@link Collections},
     * {@link Map}, {@link Number}, {@link URI} and all classes in the {@code org.drasyl} package.
     * Further objects can be added on start via the {@link DrasylConfig} or on demand via {@link
     * org.drasyl.pipeline.HandlerContext#validator}. If the {@link org.drasyl.pipeline.codec.DefaultCodec}
     * does not support these objects, a custom {@link Codec} can be added to the beginning of the
     * {@link Pipeline}.
     * </p>
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @see org.drasyl.pipeline.codec.Codec
     * @see org.drasyl.pipeline.codec.DefaultCodec
     * @see org.drasyl.pipeline.codec.TypeValidator
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(CompressedPublicKey recipient,
                                        Object payload) {
        return pipeline.processOutbound(recipient, payload);
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
        if (startSequence.isDone() && started.compareAndSet(true, false)) {
            DrasylNode self = this;
            onInternalEvent(new NodeDownEvent(Node.of(identity, endpoints)));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identity);
            shutdownSequence = new CompletableFuture<>();
            startSequence.whenComplete((t, exp) -> getInstanceHeavy().scheduleDirect(() -> {
                rejectNewConnections();
                closeConnections();
                for (int i = components.size() - 1; i >= 0; i--) {
                    components.get(i).close();
                }

                onInternalEvent(new NodeNormalTerminationEvent(Node.of(identity, endpoints)));
                LOG.info("drasyl Node with Identity '{}' has shut down", identity);
                shutdownSequence.complete(null);
                INSTANCES.remove(self);
            }));
        }

        return shutdownSequence;
    }

    private void rejectNewConnections() {
        acceptNewConnections.set(false);
    }

    @SuppressWarnings({ "java:S1905" })
    private void closeConnections() {
        // send quit message to all peers and close connections
        channelGroup.writeAndFlush(new QuitMessage(REASON_SHUTTING_DOWN))
                .addListener((ChannelGroupFutureListener) future -> future.group().close());
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
            shutdownSequence.whenComplete((t, ex) -> getInstanceHeavy().scheduleDirect(() -> {
                try {
                    for (int i = 0; i < components.size(); i++) {
                        components.get(i).open();
                    }
                    acceptNewConnections();

                    onInternalEvent(new NodeUpEvent(Node.of(identity, endpoints)));
                    LOG.info("drasyl Node with Identity '{}' has started", identity);
                    startSequence.complete(null);
                }
                catch (DrasylException e) {
                    onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identity, endpoints), e));
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
            }));
        }

        return startSequence;
    }

    private void acceptNewConnections() {
        acceptNewConnections.set(true);
    }

    /**
     * Returns the version of the node. If the version could not be read, <code>null</code> is
     * returned.
     *
     * @return the version of the node
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

    /**
     * Returns the {@link Pipeline} to allow users to add own handlers.
     *
     * @return the pipeline
     */
    public Pipeline pipeline() {
        return this.pipeline;
    }

    /**
     * Returns the {@link Identity} of this node.
     *
     * @return the {@link Identity} of this node
     */
    public Identity identity() {
        return identity;
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
