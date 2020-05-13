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
package org.drasyl.core.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.sentry.Sentry;
import io.sentry.event.User;
import org.drasyl.core.client.SuperPeerClient;
import org.drasyl.core.client.SuperPeerClientException;
import org.drasyl.core.common.message.ApplicationMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.models.Node;
import org.drasyl.core.node.connections.AutoreferentialPeerConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.node.identity.IdentityManagerException;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.NodeServerException;
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
import static org.drasyl.core.models.Code.*;

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

    public DrasylNode() throws DrasylException {
        this(ConfigFactory.load());
    }

    public DrasylNode(Config config) throws DrasylException {
        try {
            this.config = new DrasylNodeConfig(config);
            this.identityManager = new IdentityManager(this.config);
            this.peersManager = new PeersManager();
            this.messenger = new Messenger();
            this.server = new NodeServer(identityManager, messenger, peersManager, DrasylNode.WORKER_GROUP, DrasylNode.BOSS_GROUP);
            this.superPeerClient = new SuperPeerClient(this.config, identityManager, peersManager, messenger, DrasylNode.WORKER_GROUP);
            this.started = new AtomicBoolean();
            this.startSequence = new CompletableFuture<>();
            this.shutdownSequence = new CompletableFuture<>();
            messenger.getConnectionsManager().addConnection(new AutoreferentialPeerConnection(this::onEvent, identityManager, URI.create("ws://127.0.0.1:" + this.config.getServerBindPort())));
        }
        catch (ConfigException e) {
            throw new DrasylException("Couldn't load config: \n" + e.getMessage());
        }
    }

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
     * This method is responsible for the delivery of the message <code>payload</code> to the
     * recipient <code>recipient</code>.
     * <p>
     * First, the system checks whether the message is addressed to the node itself.
     * <p>
     * If this is not the case, it is checked whether the message can be sent to a client/grandson.
     * <p>
     * If this is also not possible, the message is sent to a possibly existing super peer.
     * <p>
     * If this is also not possible, an exception is thrown.
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @throws DrasylException if an error occurs during the processing
     */
    public synchronized void send(Identity recipient, byte[] payload) throws DrasylException {
        messenger.send(new ApplicationMessage(identityManager.getIdentity(), recipient, payload));
    }

    public abstract void onEvent(Event event);

    public synchronized void send(String recipient, String payload) throws DrasylException {
        send(Identity.of(recipient), payload);
    }

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
            onEvent(new Event(NODE_DOWN, new Node(identityManager.getIdentity())));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identityManager.getIdentity().getId());
            shutdownSequence = runAsync(this::loadIdentity)
                    .thenRun(this::stopSuperPeerClient)
                    .thenRun(this::stopServer)
                    .whenComplete((r, e) -> {
                        try {
                            if (e == null) {
                                onEvent(new Event(NODE_NORMAL_TERMINATION, new Node(identityManager.getIdentity())));
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

    /**
     * Should unregister from the Super Peer and stop the client. Should do nothing if the client is
     * not registered or not started.
     */
    private void stopSuperPeerClient() {
        if (config.hasSuperPeer()) {
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
            LOG.debug("The following configuration will be used:\n{}", config);
            startSequence = runAsync(this::loadIdentity)
                    .thenRun(this::startServer)
                    .thenRun(this::startSuperPeerClient)
                    .whenComplete((r, e) -> {
                        if (e == null) {
                            onEvent(new Event(NODE_UP, new Node(identityManager.getIdentity())));
                            LOG.info("drasyl Node with Identity '{}' has started", identityManager.getIdentity().getId());
                        }
                        else {
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
        if (config.hasSuperPeer()) {
            try {
                LOG.debug("Start Super Peer Client...");
                superPeerClient.open();
                LOG.debug("Super Peer started");
            }
            catch (SuperPeerClientException e) {
                throw new CompletionException(e);
            }
        }
    }
}
