/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.models.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);
    private DrasylNodeConfig config;
    private IdentityManager identityManager;
    private boolean isStarted;

    public DrasylNode() throws DrasylException {
        this(ConfigFactory.load());
    }

    public DrasylNode(Config config) throws DrasylException {
        try {
            this.config = new DrasylNodeConfig(config);
        }
        catch (ConfigException e) {
            throw new DrasylException("Couldn't load config: \n" + e.getMessage());
        }
    }

    DrasylNode(DrasylNodeConfig config, IdentityManager identityManager, boolean isStarted) {
        this.config = config;
        this.identityManager = identityManager;
        this.isStarted = isStarted;
    }

    public abstract void onEvent(Event event);

    public void send(Identity recipient, byte[] payload) {
        // implement
    }

    public void start() throws DrasylException {
        if (!isStarted) {
            LOG.info("Try starting a drasyl node (v.{})...", DrasylNode.getVersion());
            isStarted = true;
            identityManager = new IdentityManager(config.getIdentityPath());
            LOG.debug("Using identity '{}'", identityManager.getIdentity());
        }
        else {
            throw new DrasylException("This node is already started.");
        }
    }

    public void shutdown() {
        // implement
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
                        System.out.println("Node can now send messages directly to " + event.getPeer().getAddress());
                        break;
                    case PEER_RELAY:
                        System.out.println("Node can now send messages via a relay to " + event.getPeer().getAddress());
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
//        node.send(message);
//
//        // variante 2: Warte asynchron bis ndoe sendebereit ist und schicke dann nachricht
//        node.doOnOnline(() -> {
//            Message message = null;
//            node.send(message);
//        });

        node.shutdown();
    }

    /**
     * Returns the version of the bide. If this is not possible, <code>null</code> is returned.
     *
     * @return
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
