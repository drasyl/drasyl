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
import io.sentry.Sentry;
import io.sentry.event.User;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.models.Node;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.node.identity.IdentityManagerException;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.NodeServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.drasyl.core.models.Code.*;

public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);

    static {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        Sentry.getStoredClient().setRelease(DrasylNode.getVersion());
    }

    private final DrasylNodeConfig config;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final Messenger messenger;
    private final NodeServer server;
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
            this.messenger = new Messenger(identityManager, peersManager, this::onEvent);
            this.server = new NodeServer(identityManager, messenger, peersManager);
            this.started = new AtomicBoolean();
            this.startSequence = new CompletableFuture<>();
            this.shutdownSequence = new CompletableFuture<>();
        }
        catch (ConfigException e) {
            throw new DrasylException("Couldn't load config: \n" + e.getMessage());
        }
    }

    DrasylNode(DrasylNodeConfig config,
               IdentityManager identityManager,
               PeersManager peersManager,
               NodeServer server,
               Messenger messenger,
               AtomicBoolean started,
               CompletableFuture<Void> startSequence,
               CompletableFuture<Void> shutdownSequence) {
        this.config = config;
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.server = server;
        this.messenger = messenger;
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
     * @param recipient
     * @param payload
     * @throws DrasylException
     */
    public synchronized void send(Identity recipient, byte[] payload) throws DrasylException {
        messenger.send(new Message(identityManager.getIdentity(), recipient, payload));
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
     * If a super peer has been configured, a client is started which connects to this super peer.
     * Our node uses the Super Peer to discover and communicate with other nodes.
     * <p>
     * This method returns a future, which complements if all shutdown steps have been completed.
     *
     * @return
     */
    public CompletableFuture<Void> shutdown() {
        if (started.compareAndSet(true, false)) {
            // The shutdown of the node includes up to two phases, which are performed sequentially
            // 1st Phase: Stop Super Peer Client (if started)
            // 2nd Phase: Stop local server (if started)
            onEvent(new Event(NODE_DOWN, new Node(identityManager.getIdentity())));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identityManager.getIdentity().getId());
            shutdownSequence = runAsync(this::loadIdentity)
                    .thenRun(this::stopSuperPeerClient)
                    .thenRun(this::stopServer)
                    .whenComplete((r, e) -> {
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
        // FIXME: implement
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
     * This method returns a future, which complements if all components necessary for the operation
     * have been started.
     *
     * @return
     */
    public CompletableFuture<Void> start() {
        if (started.compareAndSet(false, true)) {
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
        // FIXME: implement
    }
}
