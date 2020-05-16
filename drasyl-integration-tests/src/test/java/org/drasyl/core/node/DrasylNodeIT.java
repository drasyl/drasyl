package org.drasyl.core.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.models.Code;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.models.Event;
import org.drasyl.core.server.testutils.ANSI_COLOR;
import org.drasyl.core.server.testutils.BetterArrayList;
import org.drasyl.core.server.testutils.TestHelper;
import org.junit.Ignore;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.core.models.Code.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    private List<DrasylNode> nodes;

    @BeforeEach
    public void setup(TestInfo info) {
        TestHelper.println("STARTING " + info.getDisplayName(), ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
        nodes = new ArrayList<>();
    }

    @AfterEach
    public void cleanUp(TestInfo info) {
        nodes.forEach(n -> n.shutdown().join());
        TestHelper.println("FINISHED " + info.getDisplayName(), ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);
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
        config = ConfigFactory.parseString("drasyl.server.bind-port = 22528\ndrasyl.super-peer.endpoints = []").withFallback(ConfigFactory.load("configs/DrasylNodeIT-4c4fdd0957.conf"));
        Pair<DrasylNode, Observable<Event>> superSuperPeer = createNode(config);
        assertThat(superSuperPeer.second().take(1).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), contains(NODE_UP));
        TestHelper.println("CREATED superSuperPeer", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        // super peer
        config = ConfigFactory.parseString("drasyl.server.bind-port = 22529\ndrasyl.super-peer.public-key = \"03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22528\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-9df9214d78.conf"));
        Pair<DrasylNode, Observable<Event>> superPeer = createNode(config);
        assertThat(superPeer.second().take(2).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), contains(NODE_UP, NODE_ONLINE));
        TestHelper.println("CREATED superPeer", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        // client1
        config = ConfigFactory.parseString("drasyl.server.enabled = false\ndrasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22529\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-030f018704.conf"));
        Pair<DrasylNode, Observable<Event>> client1 = createNode(config);
        assertThat(client1.second().take(2).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), contains(NODE_UP, NODE_ONLINE));
        TestHelper.println("CREATED client1", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        // client2
        config = ConfigFactory.parseString("drasyl.server.enabled = false\ndrasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22529\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-be0300f1a4.conf"));
        Pair<DrasylNode, Observable<Event>> client2 = createNode(config);
        assertThat(client2.second().take(2).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), contains(NODE_UP, NODE_ONLINE));
        TestHelper.println("CREATED client2", ANSI_COLOR.CYAN, ANSI_COLOR.REVERSED);

        //
        // send messages
        //
        Single<List<Code>> superSuperPeerMessages = superSuperPeer.second().map(e -> e.getCode()).filter(c -> c == MESSAGE).take(4).toList();
        Single<List<Code>> superPeerMessages = superPeer.second().map(e -> e.getCode()).filter(c -> c == MESSAGE).take(4).toList();
        Single<List<Code>> client1Messages = client1.second().map(e -> e.getCode()).filter(c -> c == MESSAGE).take(3).toList(); // TODO: sending to grandchildren is not yet supported
        Single<List<Code>> client2Messages = client2.second().map(e -> e.getCode()).filter(c -> c == MESSAGE).take(3).toList(); // TODO: sending to grandchildren is not yet supported

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
        assertThat(superSuperPeerMessages.blockingGet(), contains(MESSAGE, MESSAGE, MESSAGE, MESSAGE));
        assertThat(superPeerMessages.blockingGet(), contains(MESSAGE, MESSAGE, MESSAGE, MESSAGE));
        assertThat(client1Messages.blockingGet(), contains(MESSAGE, MESSAGE, MESSAGE)); // TODO: sending to grandchildren is not yet supported
        assertThat(client2Messages.blockingGet(), contains(MESSAGE, MESSAGE, MESSAGE)); // TODO: sending to grandchildren is not yet supported
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
