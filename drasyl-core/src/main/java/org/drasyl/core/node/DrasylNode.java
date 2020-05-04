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
import org.drasyl.core.server.NodeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.drasyl.core.models.Code.*;

public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);

    static {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        Sentry.getStoredClient().setRelease(DrasylNode.getVersion());
    }

    private final DrasylNodeConfig config;
    private IdentityManager identityManager;
    private PeersManager peersManager;
    private boolean isStarted;
    private NodeServer server;
    private Messenger messenger;

    public DrasylNode() throws DrasylException {
        this(ConfigFactory.load());
    }

    public DrasylNode(Config config) throws DrasylException {
        try {
            this.config = new DrasylNodeConfig(config);
            this.identityManager = new IdentityManager(this.config.getIdentityPath());
            this.peersManager = new PeersManager();
            this.messenger = new Messenger(identityManager, peersManager);
            this.server = new NodeServer(identityManager, peersManager, messenger);
        }
        catch (ConfigException e) {
            throw new DrasylException("Couldn't load config: \n" + e.getMessage());
        }
    }

    DrasylNode(DrasylNodeConfig config,
               IdentityManager identityManager,
               PeersManager peersManager, boolean isStarted,
               NodeServer server, Messenger messenger) {
        this.config = config;
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.isStarted = isStarted;
        this.server = server;
        this.messenger = messenger;
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
        if (identityManager.getIdentity().equals(recipient)) {
            onEvent(new Event(Code.MESSAGE, Pair.of(recipient, payload)));
        }
        else {
            messenger.send(new Message(identityManager.getIdentity(), recipient, payload));
        }
    }

    public abstract void onEvent(Event event);

    public synchronized void send(String recipient, String payload) throws DrasylException {
        send(Identity.of(recipient), payload);
    }

    public synchronized void send(Identity recipient, String payload) throws DrasylException {
        send(recipient, payload.getBytes());
    }

    public void shutdown() throws DrasylException {
        if (isStarted) {
            LOG.info("Stop drasyl Node with Identity {}", identityManager.getIdentity());

            // mark the node as offline, so that the local application will (hopefully) no longer try to send messages
            onEvent(new Event(NODE_OFFLINE, new Node(identityManager.getIdentity())));

            // FIXME: unregister from super peer first (if registered)...

            if (config.isServerEnabled()) {
                // ...then shut down the local server...
                LOG.info("Stop Server listening at {}:{}", config.getServerBindHost(), server.getPort());
                server.close();
                server.awaitClose();
                LOG.info("Server stopped", config.getServerBindHost());
            }

            // shutdown sequence completed
            onEvent(new Event(NODE_NORMAL_TERMINATION, new Node(identityManager.getIdentity())));

            LOG.info("drasyl Node stopped");
        }
        else {
            throw new DrasylException("This node is already shut down.");
        }
    }

    public void start() throws DrasylException {
        if (!isStarted) {
            isStarted = true;
            LOG.info("Starting drasyl Node (v.{})...", DrasylNode.getVersion());

            // first of all it must be ensured that the node has an identity...
            identityManager.loadOrCreateIdentity();
            LOG.info("Using Identity '{}'", identityManager.getIdentity());
            Sentry.getContext().setUser(new User(identityManager.getIdentity().getId(), null, null, null));

            if (config.isServerEnabled()) {
                // ...then the local server may have to be started so that the node can react to incoming messages...
                LOG.info("Start Server");
                server.open();
                server.awaitOpen();
                LOG.info("Server is now listening at {}:{}", config.getServerBindHost(), server.getPort());
            }

            // FIXME: ...last, the server should register with a super peer if configured

            // start sequence completed. node should now (hopefully) be online
            onEvent(new Event(NODE_ONLINE, new Node(identityManager.getIdentity())));

            LOG.info("drasyl Node with Identity {} started", identityManager.getIdentity());
        }
        else {
            throw new DrasylException("This node is already started.");
        }
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
}
