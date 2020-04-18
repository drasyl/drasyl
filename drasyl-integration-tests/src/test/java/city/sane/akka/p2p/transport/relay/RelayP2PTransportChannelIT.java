package city.sane.akka.p2p.transport.relay;


import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.Route;
import akka.testkit.javadsl.TestKit;
import city.sane.akka.p2p.transport.ActorSystemConfigFactory;
import city.sane.akka.p2p.transport.MyTestActor;
import city.sane.akka.p2p.transport.P2PTransportException;
import org.drasyl.core.server.RelayServerException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RelayP2PTransportChannelIT {

    RelayHandle relay;

    private ActorSystem createActorSystem(String systemName) {
        Config config = ActorSystemConfigFactory.createTestActorSystemConfig(
                relay.getConfig(),
                Map.of(
                "akka.p2p.enabled-channels", "[\"city.sane.akka.p2p.transport.relay.RelayP2PTransportChannel\"]"
                )
        );
        return ActorSystem.create(systemName, config);
    }

    @Before
    public void setUp() throws RelayServerException, URISyntaxException {
        relay = new RelayHandle(1111);
        relay.start();
    }

    @After
    public void tearDown() throws RelayServerException {
        relay.shutdown();
    }

    // Messages to local actuators should arrive
    @Test(timeout = 10 * 1000L)
    public void localMessage() throws ExecutionException, InterruptedException {
        ActorSystem system = null;
        try {
            // create actor system with two actors
            system = createActorSystem("ChatServer1");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            ActorRef actorRefBob = system.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            // send message from alice to bob
            actorRefBob.tell("Hallo Bob!", actorRefAlice);

            Assert.assertEquals("Hallo Bob!", messageReceivedBob.get());
        } finally {
            if (system != null) {
                TestKit.shutdownActorSystem(system);
            }
        }
    }

    // Messages to an ActorSelection should arrive
    @Test(timeout = 10 * 1000L)
    public void remoteActorSelection() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");
            system2 = createActorSystem("ChatServer2");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            // send message from alice to bob using an ActorSelection
            ActorSelection selectionBob = system1.actorSelection("bud://ChatServer2/user/Bob");
            selectionBob.tell("Hallo Bob!", actorRefAlice);

            Assert.assertEquals("Hallo Bob!", messageReceivedBob.get());
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
        }
    }

    // Messages to ALL should arrive all other clients
    @Test(timeout = 20 * 1000L)
    public void remoteActorSelectionAll() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        ActorSystem system3 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");
            system2 = createActorSystem("ChatServer2");
            system3 = createActorSystem("ChatServer3");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");

            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            CompletableFuture<Object> messageReceivedBob2 = new CompletableFuture<>();
            system3.actorOf(MyTestActor.probs(messageReceivedBob2), "Bob");

            // send message from alice to bob using an ActorSelection
            ActorSelection selection = system1.actorSelection("bud://ALL/user/Bob");
            selection.tell("Hallo Bobs!", actorRefAlice);

            Assert.assertEquals("Hallo Bobs!", messageReceivedBob.get());
            Assert.assertEquals("Hallo Bobs!", messageReceivedBob2.get());
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
            if (system3 != null) {
                TestKit.shutdownActorSystem(system3);
            }
        }
    }

    // Messages to ANY should arrive a Bob XOR Bob2
    @Test(timeout = 20 * 1000L)
    public void remoteActorSelectionAny() throws InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        ActorSystem system3 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");
            system2 = createActorSystem("ChatServer2");
            system3 = createActorSystem("ChatServer3");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");

            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            CompletableFuture<Object> messageReceivedBob2 = new CompletableFuture<>();
            system3.actorOf(MyTestActor.probs(messageReceivedBob2), "Bob");

            // send message from alice to bob using an ActorSelection
            ActorSelection selection = system1.actorSelection("bud://ANY/user/Bob");
            selection.tell("Hallo Bobs!", actorRefAlice);

            // wait some time to send messages
            Thread.sleep(5 * 1000L);

            assertTrue(messageReceivedBob.isDone() && !messageReceivedBob2.isDone() || !messageReceivedBob.isDone() && messageReceivedBob2.isDone());
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
            if (system3 != null) {
                TestKit.shutdownActorSystem(system3);
            }
        }
    }

    // Messages to an ActorRef resolved from an ActorSelection should arrive
    @Test(timeout = 20 * 1000L)
    public void resolvedActorRef() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");
            system2 = createActorSystem("ChatServer2");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            // send message from alice to bob using an ActorRef
            ActorSelection selectionBob = system1.actorSelection("bud://ChatServer2/user/Bob");
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(10)).toCompletableFuture().get();
            actorRefBob.tell("Hallo Bob!", actorRefAlice);

            Assert.assertEquals("Hallo Bob!", messageReceivedBob.get());
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
        }
    }

    @Ignore
    // System creation should fail if relay is not available
    @Test(expected = P2PTransportException.class)
    public void p2pFailed() {
        ActorSystem system = null;
        try {

            // create two actor systems with one actor on each system
            system = ActorSystem.create("ChatServer1", ConfigFactory.parseString("akka.p2p.relay.url = " +
                    "\"ws://localhost:25421/\"").withFallback(ConfigFactory.load()));
        } finally {
            if (system != null) {
                TestKit.shutdownActorSystem(system);
            }
        }
    }

    // Throw an exception if no connection to the relay is possible
    @Test(timeout = 20 * 1000L, expected = P2PTransportException.class)
    public void testRelayTerminated() throws Throwable {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");
            system2 = createActorSystem("ChatServer2");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            // send message from alice to bob using an ActorRef
            ActorSelection selectionBob = system1.actorSelection("bud://ChatServer2/user/Bob");
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();

            relay.shutdown();

            // wait for client to become aware of offline relay
            Thread.sleep(5 * 1000L);

            try {
                actorRefBob.tell("Hallo Bob!", actorRefAlice);
            } catch (RuntimeException e) {
                throw e.getCause();
            }
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
        }
    }

    // Should throw an exception if the wrong protocol is used
    @Test(timeout = 10 * 1000L, expected = P2PTransportException.class)
    public void wrongProtocolActorSelection() {
        ActorSystem system = null;
        try {
            system = createActorSystem("ChatServer1");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            system.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");

            system.actorSelection("akka.tcp://ChatServer2/user/Bob");
        } finally {
            if (system != null) {
                TestKit.shutdownActorSystem(system);
            }
        }
    }

    @Ignore
    @Test
    public void sendActorRef() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");
            system2 = createActorSystem("ChatServer2");

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(RemoteIT.MyActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(RemoteIT.MyActor.probs(messageReceivedBob), "Bob");

            // send message from alice to bob using an ActorRef
            ActorSelection selectionBob = system1.actorSelection("bud://ChatServer2/user/Bob");
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(10)).toCompletableFuture().get();
            actorRefBob.tell(actorRefBob, actorRefAlice);

            Assert.assertEquals(actorRefBob, messageReceivedBob.get());
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
        }
    }

    @Ignore
    @Test
    public void testHttpServer() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem("ChatServer1");

            MyHttpApp server = new MyHttpApp();
            server.startServer("localhost", 8080, system1);

            while (true) {
                Thread.sleep(1 * 1000L);
            }
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
        }
    }

    @Test
    public void createShouldFailWithDuplicateSystemName() {
        ActorSystem system1 = null;
        try {
            system1 = createActorSystem("ChatServer1");
            assertThrows(P2PTransportException.class, () -> createActorSystem("ChatServer1"));
        } finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
        }
    }

    static class MyHttpApp extends HttpApp {
        @Override
        protected Route routes() {
            return complete("Hallo Welt");
        }
    }
}
