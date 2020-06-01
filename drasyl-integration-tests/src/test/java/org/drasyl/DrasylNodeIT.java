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
import org.drasyl.event.EventCode;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.*;
import testutils.TestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.event.EventCode.*;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;

class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    private List<DrasylNode> nodes;

    @BeforeEach
    void setup(TestInfo info) {
        TestHelper.colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
        nodes = new ArrayList<>();
    }

    @AfterEach
    void cleanUp(TestInfo info) {
        nodes.forEach(n -> n.shutdown().join());
        TestHelper.colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    /**
     * This test ensures that sent application messages are delivered to the recipient. Two clients,
     * one Super Peer and one Super Super Peer, will be created for this purpose. All nodes send
     * messages to every other node (including themselves). At the end, a check is made to ensure
     * that all nodes have received all messages.
     */
    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void applicationMessagesShouldBeDelivered() throws DrasylException {
        //
        // create nodes
        //
        Config config;

        // super super peer
        config = ConfigFactory.parseString("drasyl.server.bind-port = 22528\ndrasyl.super-peer.enabled = false").withFallback(ConfigFactory.load("configs/DrasylNodeIT-4c4fdd0957.conf"));
        Pair<DrasylNode, Observable<Event>> superSuperPeer = createNode(config);
        superSuperPeer.second().filter(e -> e.getCode() == EVENT_NODE_UP).test().awaitCount(1);
        TestHelper.colorizedPrintln("CREATED superSuperPeer", COLOR_CYAN, STYLE_REVERSED);

        // super peer
        config = ConfigFactory.parseString("drasyl.server.bind-port = 22529\ndrasyl.super-peer.public-key = \"03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22528\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-9df9214d78.conf"));
        Pair<DrasylNode, Observable<Event>> superPeer = createNode(config);
        superPeer.second().filter(e -> e.getCode() == EVENT_NODE_ONLINE).test().awaitCount(1);
        TestHelper.colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

        // client1
        config = ConfigFactory.parseString("drasyl.server.enabled = false\ndrasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22529\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-030f018704.conf"));
        Pair<DrasylNode, Observable<Event>> client1 = createNode(config);
        client1.second().filter(e -> e.getCode() == EVENT_NODE_ONLINE).test().awaitCount(1);
        TestHelper.colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

        // client2
        config = ConfigFactory.parseString("drasyl.server.enabled = false\ndrasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22529\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-be0300f1a4.conf"));
        Pair<DrasylNode, Observable<Event>> client2 = createNode(config);
        client2.second().filter(e -> e.getCode() == EVENT_NODE_ONLINE).test().awaitCount(1);
        TestHelper.colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

        //
        // send messages
        //
        TestObserver<EventCode> superSuperPeerMessages = superSuperPeer.second().map(e -> e.getCode()).filter(c -> c == EVENT_MESSAGE).test();
        TestObserver<EventCode> superPeerMessages = superPeer.second().map(e -> e.getCode()).filter(c -> c == EVENT_MESSAGE).test();
        TestObserver<EventCode> client1Messages = client1.second().map(e -> e.getCode()).filter(c -> c == EVENT_MESSAGE).test();
        TestObserver<EventCode> client2Messages = client2.second().map(e -> e.getCode()).filter(c -> c == EVENT_MESSAGE).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

        Set<String> identities = Set.of("4c4fdd0957", "9df9214d78", "030f018704", "be0300f1a4");
        for (String recipient : identities) {
            if (!recipient.equals("030f018704") && !recipient.equals("be0300f1a4")) { // TODO: sending to grandchildren is not yet supported
                superSuperPeer.first().send(recipient, "Hallo Welt");
            }
            superPeer.first().send(recipient, "Hallo Welt");
            client1.first().send(recipient, "Hallo Welt");
            client2.first().send(recipient, "Hallo Welt");
        }

        //
        // verify
        //
        superSuperPeerMessages.awaitCount(4);
        superPeerMessages.awaitCount(4);
        client1Messages.awaitCount(3); // TODO: sending to grandchildren is not yet supported
        client2Messages.awaitCount(3); // TODO: sending to grandchildren is not yet supported
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
}
