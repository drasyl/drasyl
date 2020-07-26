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

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.event.PeerUnreachableEvent;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static testutils.TestHelper.colorizedPrintln;

class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    private List<DrasylNode> nodes;

    @BeforeEach
    void setup(TestInfo info) {
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
        nodes = new ArrayList<>();
    }

    @AfterEach
    void cleanUp(TestInfo info) {
        nodes.forEach(n -> n.shutdown().join());
        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    private Pair<DrasylNode, Observable<Event>> createStartedNode(DrasylConfig config) throws DrasylException {
        Pair<DrasylNode, Observable<Event>> pair = createNode(config);
        pair.first().start();
        return pair;
    }

    private Pair<DrasylNode, Observable<Event>> createNode(DrasylConfig config) throws DrasylException {
        Subject<Event> subject = ReplaySubject.<Event>create().toSerialized();
        DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                subject.onNext(event);
                if (event instanceof NodeNormalTerminationEvent) {
                    subject.onComplete();
                }
            }
        };
        nodes.add(node);

        return Pair.of(node, subject);
    }

    /**
     * Two clients, one Super Peer and one Super Super Peer will be created for this test.
     */
    @Nested
    class SuperSuperPeerAndSuperPeerAndTwoClientWhenIntraVmDiscoveryIsDisabled {
        private Pair<DrasylNode, Observable<Event>> superSuperPeer;
        private Pair<DrasylNode, Observable<Event>> superPeer;
        private Pair<DrasylNode, Observable<Event>> client1;
        private Pair<DrasylNode, Observable<Event>> client2;

        @BeforeEach
        void setUp() throws DrasylException, CryptoException {
            //
            // create nodes
            //
            DrasylConfig config;

            // super super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(13290399))
                    .identityPublicKey(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"))
                    .identityPrivateKey(CompressedPrivateKey.of("0c2945e523e1ab27c3b38ba62f0a67a21567dcfcbad4ff3fe7f8f7b202a18c93"))
                    .serverBindHost("127.0.0.1")
                    .serverBindPort(0)
                    .superPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .build();
            superSuperPeer = createStartedNode(config);
            NodeEvent superSuperPeerNodeUp = (NodeEvent) superSuperPeer.second().filter(e -> e instanceof NodeUpEvent).firstElement().blockingGet();
            int superSuperPeerPort = superSuperPeerNodeUp.getNode().getEndpoints().iterator().next().getPort();
            colorizedPrintln("CREATED superSuperPeer", COLOR_CYAN, STYLE_REVERSED);

            // super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(6518542))
                    .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                    .serverBindHost("127.0.0.1")
                    .serverBindPort(0)
                    .superPeerPublicKey(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"))
                    .superPeerEndpoints(Set.of(URI.create("ws://127.0.0.1:" + superSuperPeerPort)))
                    .intraVmDiscoveryEnabled(false)
                    .build();
            superPeer = createStartedNode(config);
            NodeEvent superPeerNodeUp = (NodeEvent) superPeer.second().filter(e -> e instanceof NodeUpEvent).firstElement().blockingGet();
            int superPeerPort = superPeerNodeUp.getNode().getEndpoints().iterator().next().getPort();
            colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(12304070))
                    .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                    .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                    .serverEnabled(false)
                    .superPeerPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .superPeerEndpoints(Set.of(URI.create("ws://127.0.0.1:" + superPeerPort)))
                    .intraVmDiscoveryEnabled(false)
                    .build();
            client1 = createStartedNode(config);
            colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(33957767))
                    .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                    .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                    .serverEnabled(false)
                    .superPeerPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .superPeerEndpoints(Set.of(URI.create("ws://127.0.0.1:" + superPeerPort)))
                    .intraVmDiscoveryEnabled(false)
                    .build();
            client2 = createStartedNode(config);
            colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

            superSuperPeer.second().filter(e -> e instanceof NodeUpEvent || e instanceof PeerDirectEvent || e instanceof PeerRelayEvent).test().awaitCount(4);
            superPeer.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(3);
            client1.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(2);
            client2.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(2);
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
            TestObserver<Event> superSuperPeerMessages = superSuperPeer.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> superPeerMessages = superPeer.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> client1Messages = client1.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> client2Messages = client2.second().filter(e -> e instanceof MessageEvent).test();

//        superSuperPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

            Set<String> identities = Set.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a",
                    "030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                    "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                    "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
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
            TestObserver<Event> superSuperPeerEvents = superSuperPeer.second().filter(e -> e instanceof PeerDirectEvent || e instanceof PeerRelayEvent).test();
            TestObserver<Event> superPeerEvents = superPeer.second().filter(e -> e instanceof PeerDirectEvent).test();
            TestObserver<Event> client1Events = client1.second().filter(e -> e instanceof PeerDirectEvent).test();
            TestObserver<Event> client2Events = client2.second().filter(e -> e instanceof PeerDirectEvent).test();

//            superSuperPeer.second().subscribe(e -> System.err.println("SSP: " + e));
//            superPeer.second().subscribe(e -> System.err.println("SP: " + e));
//            client1.second().subscribe(e -> System.err.println("C1: " + e));
//            client2.second().subscribe(e -> System.err.println("C2: " + e));

            superSuperPeerEvents.awaitCount(3);
            superPeerEvents.awaitCount(3);
            client1Events.awaitCount(1);
            client2Events.awaitCount(1);
        }

        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void shuttingDownNodeShouldCloseConnections() {
            //
            // send messages
            //
            TestObserver<Event> superSuperPeerEvents = superSuperPeer.second().filter(e -> e instanceof PeerRelayEvent).test();
            TestObserver<Event> client1Events = client1.second().filter(e -> e instanceof PeerUnreachableEvent).test();
            TestObserver<Event> client2Events = client2.second().filter(e -> e instanceof PeerUnreachableEvent).test();

            superPeer.first().shutdown().join();

            superSuperPeerEvents.awaitCount(1);
            client1Events.awaitCount(1);
            client2Events.awaitCount(1);
        }
    }

    /**
     * Two clients with enabled servers and one Super Peer will be created for this test.
     */
    @Nested
    class SuperPeerAndTwoClientWhenIntraVmDiscoveryIsDisabled {
        private Pair<DrasylNode, Observable<Event>> superPeer;
        private Pair<DrasylNode, Observable<Event>> client1;
        private Pair<DrasylNode, Observable<Event>> client2;

        @BeforeEach
        void setUp() throws DrasylException, CryptoException {
            //
            // create nodes
            //
            DrasylConfig config;

            // super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(6518542))
                    .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                    .serverBindHost("127.0.0.1")
                    .serverBindPort(0)
                    .superPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .build();
            superPeer = createStartedNode(config);
            NodeEvent superPeerNodeUp = (NodeEvent) superPeer.second().filter(e -> e instanceof NodeUpEvent).firstElement().blockingGet();
            int superPeerPort = superPeerNodeUp.getNode().getEndpoints().iterator().next().getPort();
            colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(12304070))
                    .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                    .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                    .serverBindHost("127.0.0.1")
                    .serverBindPort(0)
                    .superPeerPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .superPeerEndpoints(Set.of(URI.create("ws://127.0.0.1:" + superPeerPort)))
                    .intraVmDiscoveryEnabled(false)
                    .build();
            client1 = createStartedNode(config);
            colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(33957767))
                    .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                    .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                    .serverBindHost("127.0.0.1")
                    .serverBindPort(0)
                    .superPeerPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .superPeerEndpoints(Set.of(URI.create("ws://127.0.0.1:" + superPeerPort)))
                    .intraVmDiscoveryEnabled(false)
                    .build();
            client2 = createStartedNode(config);
            colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

            superPeer.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(3);
            client1.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(2);
            client2.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(2);
        }

        @Test
        void shouldEstablishDirectConnectionToOtherPeer() throws DrasylException, CryptoException {
            TestObserver<Event> client1RelayEvents = client1.second().filter(e -> e instanceof PeerEvent && ((PeerEvent) e).getPeer().getPublicKey().equals(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))).test();
            TestObserver<Event> client2RelayEvents = client2.second().filter(e -> e instanceof PeerEvent && ((PeerEvent) e).getPeer().getPublicKey().equals(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4")) || e instanceof MessageEvent).test();

            client1.first().send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "Hallo Welt");

            client1RelayEvents.awaitCount(2);
            client1RelayEvents.assertValueAt(0, new PeerRelayEvent(new Peer(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))));
            client1RelayEvents.assertValueAt(1, new PeerDirectEvent(new Peer(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))));
            client2RelayEvents.awaitCount(3);
            client2RelayEvents.assertValueAt(0, new PeerRelayEvent(new Peer(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))));
            client2RelayEvents.assertValueAt(1, e -> e instanceof MessageEvent);
            client2RelayEvents.assertValueAt(2, new PeerDirectEvent(new Peer(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))));
        }
    }

    /**
     * Four nodes with disabled sever and disabled super peer will be created for this test.
     */
    @Nested
    class FourClientsWhenServerAndSuperPeerAreDisabled {
        private Pair<DrasylNode, Observable<Event>> node1;
        private Pair<DrasylNode, Observable<Event>> node2;
        private Pair<DrasylNode, Observable<Event>> node3;
        private Pair<DrasylNode, Observable<Event>> node4;

        @BeforeEach
        void setUp() throws DrasylException, CryptoException {
            //
            // create nodes
            //
            DrasylConfig config;

            // super super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(13290399))
                    .identityPublicKey(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"))
                    .identityPrivateKey(CompressedPrivateKey.of("0c2945e523e1ab27c3b38ba62f0a67a21567dcfcbad4ff3fe7f8f7b202a18c93"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .build();
            node1 = createStartedNode(config);
            colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);

            // super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(6518542))
                    .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .build();
            node2 = createStartedNode(config);
            colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(12304070))
                    .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                    .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .build();
            node3 = createStartedNode(config);
            colorizedPrintln("CREATED node3", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(33957767))
                    .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                    .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .build();
            node4 = createStartedNode(config);
            colorizedPrintln("CREATED node4", COLOR_CYAN, STYLE_REVERSED);

            node1.second().filter(e -> e instanceof NodeUpEvent || e instanceof PeerDirectEvent).test().awaitCount(3);
            node2.second().filter(e -> e instanceof NodeUpEvent || e instanceof PeerDirectEvent).test().awaitCount(3);
            node3.second().filter(e -> e instanceof NodeUpEvent || e instanceof PeerDirectEvent).test().awaitCount(3);
            node4.second().filter(e -> e instanceof NodeUpEvent || e instanceof PeerDirectEvent).test().awaitCount(3);
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
            TestObserver<Event> superSuperPeerMessages = node1.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> superPeerMessages = node2.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> client1Messages = node3.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> client2Messages = node4.second().filter(e -> e instanceof MessageEvent).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

            Set<String> identities = Set.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a",
                    "030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                    "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                    "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
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
            TestObserver<Event> node1Events = node1.second().filter(e -> e instanceof PeerDirectEvent).test();
            TestObserver<Event> node2Events = node2.second().filter(e -> e instanceof PeerDirectEvent).test();
            TestObserver<Event> node3Events = node3.second().filter(e -> e instanceof PeerDirectEvent).test();
            TestObserver<Event> node4Events = node4.second().filter(e -> e instanceof PeerDirectEvent).test();

            node1Events.awaitCount(3);
            node2Events.awaitCount(3);
            node3Events.awaitCount(3);
            node4Events.awaitCount(3);
        }
    }

    /**
     * Four nodes with disabled sever, disabled super peer and disabled intra vm discovery will be
     * created for this test.
     */
    @Nested
    class FourNodesWhenServerAndSuperPeerAndIntraVmDiscoveryAreDisabled {
        private Pair<DrasylNode, Observable<Event>> node1;
        private Pair<DrasylNode, Observable<Event>> node2;
        private Pair<DrasylNode, Observable<Event>> node3;
        private Pair<DrasylNode, Observable<Event>> node4;

        @BeforeEach
        void setUp() throws DrasylException, CryptoException {
            //
            // create nodes
            //
            DrasylConfig config;

            // super super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(13290399))
                    .identityPublicKey(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"))
                    .identityPrivateKey(CompressedPrivateKey.of("0c2945e523e1ab27c3b38ba62f0a67a21567dcfcbad4ff3fe7f8f7b202a18c93"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .build();
            node1 = createStartedNode(config);
            node1.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1);
            colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);

            // super peer
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(6518542))
                    .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                    .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .build();
            node2 = createStartedNode(config);
            node2.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1);
            colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);

            // client1
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(12304070))
                    .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                    .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .build();
            node3 = createStartedNode(config);
            node3.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1);
            colorizedPrintln("CREATED node3", COLOR_CYAN, STYLE_REVERSED);

            // client2
            config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(33957767))
                    .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                    .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .build();
            node4 = createStartedNode(config);
            node4.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1);
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
            TestObserver<Event> superSuperPeerMessages = node1.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> superPeerMessages = node2.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> client1Messages = node3.second().filter(e -> e instanceof MessageEvent).test();
            TestObserver<Event> client2Messages = node4.second().filter(e -> e instanceof MessageEvent).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

            node1.first().send("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a", "Hallo Welt");
            node2.first().send("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "Hallo Welt");
            node3.first().send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", "Hallo Welt");
            node4.first().send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "Hallo Welt");

            //
            // verify
            //
            superSuperPeerMessages.awaitCount(1);
            superPeerMessages.awaitCount(1);
            client1Messages.awaitCount(1);
            client2Messages.awaitCount(1);
        }
    }

    /**
     * Single non-started node.
     */
    @Nested
    class SingleNonStartedNode {
        private Pair<DrasylNode, Observable<Event>> node;

        @BeforeEach
        void setUp() throws DrasylException, CryptoException {
            //
            // create nodes
            //
            DrasylConfig config = DrasylConfig.newBuilder()
                    .identityProofOfWork(ProofOfWork.of(33957767))
                    .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                    .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                    .serverEnabled(false)
                    .superPeerEnabled(false)
                    .build();
            node = createNode(config);
            colorizedPrintln("CREATED node", COLOR_CYAN, STYLE_REVERSED);
        }

        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void sendToSelfShouldThrowException() {
            assertThrows(ExecutionException.class, () -> node.first().send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "Hallo Welt").get());
        }

        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void sendToAnOtherPeerShouldThrowException() {
            assertThrows(ExecutionException.class, () -> node.first().send("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "Hallo Welt").get());
        }
    }
}
