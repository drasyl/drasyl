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
    private final DrasylNodeConfig config;
    private IdentityManager identityManager;
    private PeersManager peersManager;
    private boolean isStarted;
    private NodeServer server;
    private Messenger messenger;

    static {
        Sentry.getStoredClient().setRelease(DrasylNode.getVersion());
    }

    public DrasylNode() throws DrasylException {
        this(ConfigFactory.load());
    }

    public DrasylNode(Config config) throws DrasylException {
        try {
            this.config = new DrasylNodeConfig(config);
            this.peersManager = new PeersManager();
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
            onEvent(new Event(Code.MESSAGE, null, null, payload));
        }
        else {
            messenger.send(new Message(identityManager.getIdentity(), recipient, payload));
        }
    }

    public synchronized void send(String recipient, byte[] payload) throws DrasylException {
        send(Identity.of(recipient), payload);
    }

    public synchronized void send(Identity recipient, String payload) throws DrasylException {
        send(recipient, payload.getBytes());
    }

    public synchronized void send(String recipient, String payload) throws DrasylException {
        send(Identity.of(recipient), payload);
    }

    public abstract void onEvent(Event event);

    public void start() throws DrasylException {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");

        if (!isStarted) {
            LOG.info("Try starting a drasyl node (v.{})...", DrasylNode.getVersion());
            isStarted = true;
            identityManager = new IdentityManager(config.getIdentityPath());
            LOG.debug("Using identity '{}'", identityManager.getIdentity());
            Sentry.getContext().setUser(new User(identityManager.getIdentity().getId(), null, null, null));
            messenger = new Messenger(this, identityManager, peersManager);

            server = new NodeServer(identityManager, messenger, peersManager);
            server.open();
            server.awaitOpen();
            LOG.info("Server started at {}:{}", config.getServerBindHost(), config.getServerBindPort());

            // FIXME: connect to super peer

            onEvent(new Event(NODE_ONLINE, new Node(identityManager.getIdentity())));
        }
        else {
            throw new DrasylException("This node is already started.");
        }
    }

    /**
     * Returns the version of the bide. If this is not possible, <code>null</code> is returned.
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

    public static void main(String[] args) throws DrasylException {
        // create node
        DrasylNode node = new DrasylNode() {
            @Override
            public void onEvent(Event event) {
                System.out.println("Event received: " + event);

                switch (event.getCode()) {
                    case NODE_ONLINE:
                        System.out.println("Node is online with the following Id: " + event.getNode().getAddress());
                        break;
                    case NODE_OFFLINE:
                        System.out.println("Node is offline");
                        break;
                    case PEER_P2P:
                        System.out.println("Node can now sendMSG messages directly to " + event.getPeer().getAddress());
                        break;
                    case PEER_RELAY:
                        System.out.println("Node can now sendMSG messages via a relay to " + event.getPeer().getAddress());
                        break;
                }
            }
        };

//        // variante 1: Warte synchron bis node sendebereit ist und schicke dann nachricht
//        while (!node.isOnline()) {
//            Thread.sleep(1 * 1000L);
//        }
//
//        Message message = null;
//        node.sendMSG(message);
//
//        // variante 2: Warte asynchron bis ndoe sendebereit ist und schicke dann nachricht
//        node.doOnOnline(() -> {
//            Message message = null;
//            node.sendMSG(message);
//        });

        node.start();
    }

    public void shutdown() throws DrasylException {
        LOG.info("Stop Server at {}:{}", config.getServerBindHost(), config.getServerBindPort());
        server.close();
        server.awaitClose();
        LOG.debug("Server stopped at {}:{}", config.getServerBindHost(), config.getServerBindPort());

        onEvent(new Event(NODE_OFFLINE, new Node(identityManager.getIdentity())));

        // FIXME: disconnect from super peer

        onEvent(new Event(NODE_NORMAL_TERMINATION, new Node(identityManager.getIdentity())));
    }
}
