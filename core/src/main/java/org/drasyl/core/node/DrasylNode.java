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
import com.typesafe.config.ConfigFactory;
import org.drasyl.core.crypto.CompressedPublicKey;
import org.drasyl.core.node.models.Event;

public abstract class DrasylNode {
    public DrasylNode() {
        this(ConfigFactory.load());
    }

    public DrasylNode(Config config) {
        // implement
    }

    abstract void onMessage(byte[] payload);

    abstract void onEvent(Event event);

    public void send(CompressedPublicKey recipient, byte[] payload) {
        // implement
    }

    public void close() {
        // implement
    }

    public static void main(String[] args) {
        // create node
        DrasylNode node = new DrasylNode() {
            @Override
            void onMessage(byte[] payload) {
                System.out.println("Message received: " + payload);
            }

            @Override
            void onEvent(Event event) {
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

        node.close();
    }
}
