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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.event.Event;
import org.drasyl.event.EventType;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.event.EventType.EVENT_MESSAGE;
import static org.drasyl.event.EventType.EVENT_NODE_ONLINE;
import static org.drasyl.event.EventType.EVENT_NODE_UP;
import static org.drasyl.event.EventType.EVENT_PEER_DIRECT;
import static org.drasyl.event.EventType.EVENT_PEER_RELAY;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static testutils.TestHelper.colorizedPrintln;

class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    private List<DrasylNode> nodes;

    @BeforeEach
    void setup(TestInfo info) {
        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
        nodes = new ArrayList<>();
    }

    @AfterEach
    void cleanUp(TestInfo info) {
        nodes.forEach(n -> n.shutdown().join());
        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    private Pair<DrasylNode, Observable<Event>> createNode(Config config) throws DrasylException {
        Subject<Event> subject = ReplaySubject.create();
        DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                subject.onNext(event);
            }
        };
        node.start();

        nodes.add(node);

        return Pair.of(node, subject);
    }

    /**
     * Two clients, one Super Peer and one Super Super Peer will be created for this test.
     */
    @Nested
    class WhenIntraVmDiscoveryIsDisabled {
        private Pair<DrasylNode, Observable<Event>> superSuperPeer;
        private Pair<DrasylNode, Observable<Event>> superPeer;
        private Pair<DrasylNode, Observable<Event>> client1;
        private Pair<DrasylNode, Observable<Event>> client2;

        @BeforeEach
        void setUp() throws DrasylException {
            //
            // create nodes
            //
            Config config;

            // super super peer
            config = ConfigFactory.parseString("drasyl.super-peer.enabled = false\n" +
                    "drasyl.intra-vm-discovery.enabled = false")
                    .withFallback(ConfigFactory.load("configs/DrasylNodeIT-4c4fdd0957.conf"));
            superSuperPeer = createNode(config);
            Event superSuperPeerNodeUp = superSuperPeer.second().filter(e -> e.getType() == EVENT_NODE_UP).firstElement().blockingGet();
            int superSuperPeerPort = superSuperPeerNodeUp.getNode().getEndpoints().iterator().next().getPort();
            colorizedPrintln("CREATED superSuperPeer", COLOR_CYAN, STYLE_REVERSED);

            // super peer
            config = ConfigFactory.parseString("drasyl.super-peer.public-key = \"03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a\"\n" +
                    "drasyl.super-peer.endpoints = [\"ws://127.0.0.1:" + superSuperPeerPort + "\"]\n" +
                    "drasyl.intra-vm-discovery.enabled = false")
                    .withFallback(ConfigFactory.load("configs/DrasylNodeIT-9df9214d78.conf"));
            superPeer = createNode(config);
            Event superPeerNodeUp = superPeer.second().filter(e -> e.getType() == EVENT_NODE_UP).firstElement().blockingGet();
            int superPeerPort = superPeerNodeUp.getNode().getEndpoints().iterator().next().getPort();
            superPeer.second().filter(e -> e.getType() == EVENT_NODE_ONLINE).test().awaitCount(1);
            colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\n" +
                    "drasyl.super-peer.endpoints = [\"ws://127.0.0.1:" + superPeerPort + "\"]\n" +
                    "drasyl.intra-vm-discovery.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-030f018704.conf"));
            client1 = createNode(config);
            client1.second().filter(e -> e.getType() == EVENT_NODE_ONLINE).test().awaitCount(1);
            colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\n" +
                    "drasyl.super-peer.endpoints = [\"ws://127.0.0.1:" + superPeerPort + "\"]\n" +
                    "drasyl.intra-vm-discovery.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-be0300f1a4.conf"));
            client2 = createNode(config);
            client2.second().filter(e -> e.getType() == EVENT_NODE_ONLINE).test().awaitCount(1);
            colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);
        }

        /**
         * This test ensures that sent application messages are delivered to the recipient. All
         * nodes send messages to every other node (including themselves). At the end, a check is
         * made to ensure that all nodes have received all messages.
         */
        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void applicationMessagesShouldBeDelivered() throws DrasylException {
            //
            // send messages
            //
            TestObserver<EventType> superSuperPeerMessages = superSuperPeer.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> superPeerMessages = superPeer.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> client1Messages = client1.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> client2Messages = client2.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

            Set<String> identities = Set.of("4c4fdd0957", "9df9214d78", "030f018704", "be0300f1a4");
            for (String recipient : identities) {
                superSuperPeer.first().send(recipient, "Hallo Welt");
                superPeer.first().send(recipient, "Hallo Welt");
                client1.first().send(recipient, "Hallo Welt");
                client2.first().send(recipient, "Hallo Welt");
            }

            //
            // verify
            //
            superSuperPeerMessages.awaitCount(4);
            superPeerMessages.awaitCount(4);
            client1Messages.awaitCount(4);
            client2Messages.awaitCount(4);
        }

        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void correctPeerEventsShouldBeEmitted() {
            //
            // send messages
            //
            TestObserver<EventType> superSuperPeerEvents = superSuperPeer.second().map(Event::getType).filter(EventType::isPeerEvent).test();
            TestObserver<EventType> superPeerEvents = superPeer.second().map(Event::getType).filter(EventType::isPeerEvent).test();
            TestObserver<EventType> client1Events = client1.second().map(Event::getType).filter(EventType::isPeerEvent).test();
            TestObserver<EventType> client2Events = client2.second().map(Event::getType).filter(EventType::isPeerEvent).test();

//            superSuperPeer.second().subscribe(e -> System.err.println("SSP: " + e));
//            superPeer.second().subscribe(e -> System.err.println("SP: " + e));
//            client1.second().subscribe(e -> System.err.println("C1: " + e));
//            client2.second().subscribe(e -> System.err.println("C2: " + e));

            superSuperPeerEvents.awaitCount(3);
            superSuperPeerEvents.assertValues(EVENT_PEER_DIRECT, EVENT_PEER_RELAY, EVENT_PEER_RELAY);
            superPeerEvents.awaitCount(3);
            superPeerEvents.assertValues(EVENT_PEER_DIRECT, EVENT_PEER_DIRECT, EVENT_PEER_DIRECT);
            client1Events.awaitCount(1);
            client1Events.assertValues(EVENT_PEER_DIRECT);
            client2Events.awaitCount(1);
            client2Events.assertValues(EVENT_PEER_DIRECT);
        }
    }

    /**
     * Four nodes with disabled sever and disabled super peer will be created for this test.
     */
    @Nested
    class WhenServerAndSuperPeerAreDisabled {
        private Pair<DrasylNode, Observable<Event>> node1;
        private Pair<DrasylNode, Observable<Event>> node2;
        private Pair<DrasylNode, Observable<Event>> node3;
        private Pair<DrasylNode, Observable<Event>> node4;

        @BeforeEach
        void setUp() throws DrasylException {
            //
            // create nodes
            //
            Config config;

            // super super peer
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false")
                    .withFallback(ConfigFactory.load("configs/DrasylNodeIT-4c4fdd0957.conf"));
            node1 = createNode(config);
            node1.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);

            // super peer
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false")
                    .withFallback(ConfigFactory.load("configs/DrasylNodeIT-9df9214d78.conf"));
            node2 = createNode(config);
            node2.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-030f018704.conf"));
            node3 = createNode(config);
            node3.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node3", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-be0300f1a4.conf"));
            node4 = createNode(config);
            node4.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node4", COLOR_CYAN, STYLE_REVERSED);
        }

        /**
         * This test ensures that sent application messages are delivered to the recipient. All
         * nodes send messages to every other node (including themselves). At the end, a check is
         * made to ensure that all nodes have received all messages.
         */
        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void applicationMessagesShouldBeDelivered() throws DrasylException {
            //
            // send messages
            //
            TestObserver<EventType> superSuperPeerMessages = node1.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> superPeerMessages = node2.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> client1Messages = node3.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> client2Messages = node4.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

            Set<String> identities = Set.of("4c4fdd0957", "9df9214d78", "030f018704", "be0300f1a4");
            for (String recipient : identities) {
                node1.first().send(recipient, "Hallo Welt");
                node2.first().send(recipient, "Hallo Welt");
                node3.first().send(recipient, "Hallo Welt");
                node4.first().send(recipient, "Hallo Welt");
            }

            //
            // verify
            //
            superSuperPeerMessages.awaitCount(4);
            superPeerMessages.awaitCount(4);
            client1Messages.awaitCount(4);
            client2Messages.awaitCount(4);
        }

        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void correctPeerEventsShouldBeEmitted() {
            //
            // send messages
            //
            TestObserver<EventType> node1Events = node1.second().map(Event::getType).filter(EventType::isPeerEvent).test();
            TestObserver<EventType> node2Events = node2.second().map(Event::getType).filter(EventType::isPeerEvent).test();
            TestObserver<EventType> node3Events = node3.second().map(Event::getType).filter(EventType::isPeerEvent).test();
            TestObserver<EventType> node4Events = node4.second().map(Event::getType).filter(EventType::isPeerEvent).test();

            node1Events.awaitCount(3);
            node1Events.assertValues(EVENT_PEER_DIRECT, EVENT_PEER_DIRECT, EVENT_PEER_DIRECT);
            node2Events.awaitCount(3);
            node2Events.assertValues(EVENT_PEER_DIRECT, EVENT_PEER_DIRECT, EVENT_PEER_DIRECT);
            node3Events.awaitCount(3);
            node3Events.assertValues(EVENT_PEER_DIRECT, EVENT_PEER_DIRECT, EVENT_PEER_DIRECT);
            node4Events.awaitCount(3);
            node4Events.assertValues(EVENT_PEER_DIRECT, EVENT_PEER_DIRECT, EVENT_PEER_DIRECT);
        }
    }

    /**
     * Four nodes with disabled sever, disabled super peer and disabled intra vm discovery will be
     * created for this test.
     */
    @Nested
    class WhenServerAndSuperPeerAndIntraVmDiscoveryAreDisabled {
        private Pair<DrasylNode, Observable<Event>> node1;
        private Pair<DrasylNode, Observable<Event>> node2;
        private Pair<DrasylNode, Observable<Event>> node3;
        private Pair<DrasylNode, Observable<Event>> node4;

        @BeforeEach
        void setUp() throws DrasylException {
            //
            // create nodes
            //
            Config config;

            // super super peer
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false")
                    .withFallback(ConfigFactory.load("configs/DrasylNodeIT-4c4fdd0957.conf"));
            node1 = createNode(config);
            node1.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);

            // super peer
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false")
                    .withFallback(ConfigFactory.load("configs/DrasylNodeIT-9df9214d78.conf"));
            node2 = createNode(config);
            node2.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-030f018704.conf"));
            node3 = createNode(config);
            node3.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node3", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = ConfigFactory.parseString("drasyl.server.enabled = false\n" +
                    "drasyl.super-peer.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-be0300f1a4.conf"));
            node4 = createNode(config);
            node4.second().filter(e -> e.getType() == EVENT_NODE_UP).test().awaitCount(1);
            colorizedPrintln("CREATED node4", COLOR_CYAN, STYLE_REVERSED);
        }

        /**
         * This test ensures that loopback message discovery work. Every node an application
         * messages to itself. At the end, a check is made to ensure that all nodes have received a
         * message.
         */
        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void applicationMessagesShouldBeDelivered() throws DrasylException {
            //
            // send messages
            //
            TestObserver<EventType> superSuperPeerMessages = node1.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> superPeerMessages = node2.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> client1Messages = node3.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();
            TestObserver<EventType> client2Messages = node4.second().map(e -> e.getType()).filter(c -> c == EVENT_MESSAGE).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

            node1.first().send("4c4fdd0957", "Hallo Welt");
            node2.first().send("9df9214d78", "Hallo Welt");
            node3.first().send("030f018704", "Hallo Welt");
            node4.first().send("be0300f1a4", "Hallo Welt");

            //
            // verify
            //
            superSuperPeerMessages.awaitCount(1);
            superPeerMessages.awaitCount(1);
            client1Messages.awaitCount(1);
            client2Messages.awaitCount(1);
        }
    }
}
