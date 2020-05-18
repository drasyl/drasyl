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

import ch.qos.logback.classic.LoggerContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.sentry.Sentry;
import io.sentry.event.User;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.LoopbackPeerConnection;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerException;
import org.drasyl.peer.connection.superpeer.SuperPeerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.drasyl.event.EventCode.*;
import static org.drasyl.peer.connection.PeerConnection.CloseReason.REASON_SHUTTING_DOWN;

/**
 * Represents a node in the drasyl Overlay Network. Applications that want to run on drasyl must
 * implement this class.
 * <p>
 * Example usage:
 * <pre> {@code
 * DrasylNode node = new DrasylNode() {
 *   @Override
 *   public void onEvent(Event event) {
 *     // handle incoming events (messages) here
 *     System.out.println("Event received: " + event);
 *   }
 * };
 * node.start();
 *
 * // wait till EVENT_NODE_ONLINE has been received
 *
 * // send message to another node
 * node.send("025eb0dc5d", "Hello World");
 *
 * // shutdown node
 * node.shutdown();
 * </pre>
 */
@SuppressWarnings({ "java:S107" })
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
        WORKER_GROUP = new NioEventLoopGroup(Math.min(2, ForkJoinPool.commonPool().getParallelism() * 2 / 3 - 2));
        BOSS_GROUP = new NioEventLoopGroup(2);
    }

    private final DrasylNodeConfig config;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final Messenger messenger;
    private final NodeServer server;
    private final SuperPeerClient superPeerClient;
    private final AtomicBoolean started;
    private CompletableFuture<Void> startSequence;
    private CompletableFuture<Void> shutdownSequence;

    /**
     * Creates a new drasyl Node.
     */
    public DrasylNode() throws DrasylException {
        this(ConfigFactory.load());
    }

    /**
     * Creates a new drasyl Node with the given <code>config</code>.
     *
     * @param config
     */
    public DrasylNode(Config config) throws DrasylException {
        try {
            this.config = new DrasylNodeConfig(config);
            this.identityManager = new IdentityManager(this.config);
            this.peersManager = new PeersManager();
            this.messenger = new Messenger();
            this.server = new NodeServer(identityManager, messenger, peersManager, config, DrasylNode.WORKER_GROUP, DrasylNode.BOSS_GROUP);
            this.superPeerClient = new SuperPeerClient(this.config, identityManager, peersManager, messenger, DrasylNode.WORKER_GROUP, this::onEvent);
            this.started = new AtomicBoolean();
            this.startSequence = new CompletableFuture<>();
            this.shutdownSequence = new CompletableFuture<>();

            // set config level of all drasyl loggers
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLoggerList().stream().filter(l -> l.getName().startsWith("org.drasyl")).forEach(l -> l.setLevel(this.config.getLoglevel()));
        }
        catch (ConfigException e) {
            throw new DrasylException("Couldn't load config: \n" + e.getMessage());
        }
    }

    /**
     * Sends <code>event</code> to the application and tells it information about the local node,
     * other peers, connections or incoming messages.
     *
     * @param event
     */
    public abstract void onEvent(Event event);

    DrasylNode(DrasylNodeConfig config,
               IdentityManager identityManager,
               PeersManager peersManager,
               Messenger messenger,
               NodeServer server,
               SuperPeerClient superPeerClient,
               AtomicBoolean started,
               CompletableFuture<Void> startSequence,
               CompletableFuture<Void> shutdownSequence) {
        this.config = config;
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.messenger = messenger;
        this.server = server;
        this.superPeerClient = superPeerClient;
        this.started = started;
        this.startSequence = startSequence;
        this.shutdownSequence = shutdownSequence;
    }

    public synchronized void send(String recipient, byte[] payload) throws DrasylException {
        send(Identity.of(recipient), payload);
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link DrasylException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @throws DrasylException if an error occurs during the processing
     */
    public synchronized void send(Identity recipient, byte[] payload) throws DrasylException {
        messenger.send(new ApplicationMessage(identityManager.getIdentity(), recipient, payload));
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link DrasylException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @throws DrasylException if an error occurs during the processing
     */
    public synchronized void send(String recipient, String payload) throws DrasylException {
        send(Identity.of(recipient), payload);
    }

    /**
     * Sends the content of <code>payload</code> to the identity <code>recipient</code>. Throws a
     * {@link DrasylException} if the message could not be sent to the recipient or a super peer.
     * Important: Just because no exception was thrown does not automatically mean that the message
     * could be delivered. Delivery confirmations must be implemented by the application.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @throws DrasylException if an error occurs during the processing
     */
    public synchronized void send(Identity recipient, String payload) throws DrasylException {
        send(recipient, payload.getBytes());
    }

    /**
     * Shut the Drasyl node down.
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
            // The shutdown of the node includes up to two phases, which are performed sequentially
            // 1st Phase: Stop Super Peer Client (if started)
            // 2nd Phase: Stop local server (if started)
            onEvent(new Event(EVENT_NODE_DOWN, new Node(identityManager.getIdentity())));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identityManager.getIdentity().getId());
            shutdownSequence = runAsync(this::stopSuperPeerClient)
                    .thenRun(this::stopServer)
                    .thenRun(this::destroyLoopbackPeerConnection)
                    .whenComplete((r, e) -> {
                        try {
                            if (e == null) {
                                onEvent(new Event(EVENT_NODE_NORMAL_TERMINATION, new Node(identityManager.getIdentity())));
                                LOG.info("drasyl Node with Identity '{}' has shut down", identityManager.getIdentity().getId());
                            }
                            else {
                                started.set(false);

                                // passthrough exception
                                if (e instanceof CompletionException) {
                                    throw (CompletionException) e;
                                }
                                else {
                                    throw new CompletionException(e);
                                }
                            }
                        }
                        finally {
                            INSTANCES.remove(self);
                        }
                    });
        }

        return shutdownSequence;
    }

    private void loadIdentity() {
        try {
            identityManager.loadOrCreateIdentity();
            LOG.debug("Using Identity '{}'", identityManager.getIdentity());
            Sentry.getContext().setUser(new User(identityManager.getIdentity().getId(), null, null, null));
        }
        catch (IdentityManagerException e) {
            throw new CompletionException(e);
        }
    }

    /**
     * Should unregister from the Super Peer and stop the client. Should do nothing if the client is
     * not registered or not started.
     */
    private void stopSuperPeerClient() {
        if (config.isSuperPeerEnabled()) {
            LOG.info("Stop Super Peer Client...");
            superPeerClient.close();
            LOG.info("Super Peer Client stopped");
        }
    }

    /**
     * This method should stop the server. If the server is not running, the method should do
     * nothing.
     */
    private void stopServer() {
        if (config.isServerEnabled()) {
            LOG.info("Stop Server listening at {}:{}...", config.getServerBindHost(), server.getPort());
            server.close();
            LOG.info("Server stopped");
        }
    }

    private void destroyLoopbackPeerConnection() {
        messenger.getConnectionsManager().closeConnectionsOfType(LoopbackPeerConnection.class, REASON_SHUTTING_DOWN);
    }

    /**
     * Start the Drasyl node.
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
            // The start of the node includes up to three phases, which are performed sequentially
            // 1st Phase: Load identity (and create if necessary)
            // 2nd Phase: Start local server (if enabled)
            // 3rd Phase: Start Super Peer Client (if declared)
            LOG.info("Start drasyl Node v{}...", DrasylNode.getVersion());
            LOG.debug("The following configuration will be used: {}", config);
            startSequence = runAsync(this::loadIdentity)
                    .thenRun(this::createLoopbackPeerConnection)
                    .thenRun(this::startServer)
                    .thenRun(this::startSuperPeerClient)
                    .whenComplete((r, e) -> {
                        if (e == null) {
                            onEvent(new Event(EVENT_NODE_UP, new Node(identityManager.getIdentity())));
                            LOG.info("drasyl Node with Identity '{}' has started", identityManager.getIdentity().getId());
                        }
                        else {
                            onEvent(new Event(EVENT_NODE_UNRECOVERABLE_ERROR, new Node(identityManager.getIdentity())));
                            LOG.info("Could not start drasyl Node: {}", e.getMessage());
                            LOG.info("Stop all running components...");
                            this.stopServer();
                            this.stopSuperPeerClient();

                            LOG.info("All components stopped");
                            started.set(false);

                            // passthrough exception
                            if (e instanceof CompletionException) {
                                throw (CompletionException) e;
                            }
                            else {
                                throw new CompletionException(e);
                            }
                        }
                    });
        }

        return startSequence;
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

    private void createLoopbackPeerConnection() {
        new LoopbackPeerConnection(this::onEvent, identityManager.getIdentity(), URI.create("ws://127.0.0.1:" + config.getServerBindPort()), messenger.getConnectionsManager());
    }

    /**
     * If activated, the local server should be started in this method. Method should block and wait
     * until the server is running.
     */
    private void startServer() {
        if (config.isServerEnabled()) {
            try {
                LOG.debug("Start Server...");
                server.open();
                LOG.debug("Server is now listening at {}:{}", config.getServerBindHost(), server.getPort());
            }
            catch (NodeServerException e) {
                throw new CompletionException(e);
            }
        }
    }

    /**
     * Method should wait until client has been started, but not until client has registered with
     * the super peer.
     */
    private void startSuperPeerClient() {
        if (config.isSuperPeerEnabled()) {
            try {
                LOG.debug("Start Super Peer Client...");
                superPeerClient.open(server.getEntryPoints());
                LOG.debug("Super Peer started");
            }
            catch (Exception e) {
                throw new CompletionException(e);
            }
        }
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
