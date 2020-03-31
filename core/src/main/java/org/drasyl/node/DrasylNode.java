package org.drasyl.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public abstract class DrasylNode {
    public DrasylNode() {
        this(ConfigFactory.load());
    }

    public DrasylNode(Config config) {
        // implement
    }

    abstract void onMessage(byte[] payload);

    abstract void onEvent(Event event);

    public void send(Object recipient, byte[] payload) {
        // implement
    }

    public void start() {
        // implement
    }

    public void shutdown() {
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

        node.shutdown();
    }
}
