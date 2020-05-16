package org.drasyl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.event.Event;
import org.drasyl.event.EventCode;
import testutils.TestHelper;
import org.drasyl.util.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static org.hamcrest.MatcherAssert.assertThat;

public class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    private List<DrasylNode> nodes;

    @BeforeEach
    public void setup(TestInfo info) {
        TestHelper.colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
        nodes = new ArrayList<>();
    }

    @AfterEach
    public void cleanUp(TestInfo info) {
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
        MatcherAssert.assertThat(superSuperPeer.second().take(1).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), Matchers.contains(EventCode.EVENT_NODE_UP));
        TestHelper.colorizedPrintln("CREATED superSuperPeer", COLOR_CYAN, STYLE_REVERSED);

        // super peer
        config = ConfigFactory.parseString("drasyl.server.bind-port = 22529\ndrasyl.super-peer.public-key = \"03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22528\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-9df9214d78.conf"));
        Pair<DrasylNode, Observable<Event>> superPeer = createNode(config);
        assertThat(superPeer.second().take(2).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), Matchers.contains(EventCode.EVENT_NODE_UP, EventCode.EVENT_NODE_ONLINE));
        TestHelper.colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

        // client1
        config = ConfigFactory.parseString("drasyl.server.enabled = false\ndrasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22529\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-030f018704.conf"));
        Pair<DrasylNode, Observable<Event>> client1 = createNode(config);
        assertThat(client1.second().take(2).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), Matchers.contains(EventCode.EVENT_NODE_UP, EventCode.EVENT_NODE_ONLINE));
        TestHelper.colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

        // client2
        config = ConfigFactory.parseString("drasyl.server.enabled = false\ndrasyl.super-peer.public-key = \"030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"\ndrasyl.super-peer.endpoints = [\"ws://127.0.0.1:22529\"]").withFallback(ConfigFactory.load("configs/DrasylNodeIT-be0300f1a4.conf"));
        Pair<DrasylNode, Observable<Event>> client2 = createNode(config);
        assertThat(client2.second().take(2).toList().blockingGet().stream().map(Event::getCode).collect(Collectors.toList()), Matchers.contains(EventCode.EVENT_NODE_UP, EventCode.EVENT_NODE_ONLINE));
        TestHelper.colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

        //
        // send messages
        //
        Single<List<EventCode>> superSuperPeerMessages = superSuperPeer.second().map(e -> e.getCode()).filter(c -> c == EventCode.EVENT_MESSAGE).take(4).toList();
        Single<List<EventCode>> superPeerMessages = superPeer.second().map(e -> e.getCode()).filter(c -> c == EventCode.EVENT_MESSAGE).take(4).toList();
        Single<List<EventCode>> client1Messages = client1.second().map(e -> e.getCode()).filter(c -> c == EventCode.EVENT_MESSAGE).take(3).toList(); // TODO: sending to grandchildren is not yet supported
        Single<List<EventCode>> client2Messages = client2.second().map(e -> e.getCode()).filter(c -> c == EventCode.EVENT_MESSAGE).take(3).toList(); // TODO: sending to grandchildren is not yet supported

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
        assertThat(superSuperPeerMessages.blockingGet(), Matchers.contains(EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE));
        assertThat(superPeerMessages.blockingGet(), Matchers.contains(EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE));
        assertThat(client1Messages.blockingGet(), Matchers.contains(EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE)); // TODO: sending to grandchildren is not yet supported
        assertThat(client2Messages.blockingGet(), Matchers.contains(EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE, EventCode.EVENT_MESSAGE)); // TODO: sending to grandchildren is not yet supported
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
