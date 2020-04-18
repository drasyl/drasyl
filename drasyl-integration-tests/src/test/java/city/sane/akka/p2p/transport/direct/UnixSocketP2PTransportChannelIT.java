package city.sane.akka.p2p.transport.direct;


import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import city.sane.akka.p2p.transport.ActorSystemConfigFactory;
import city.sane.akka.p2p.transport.MyTestActor;
import city.sane.akka.p2p.transport.P2PTransportException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertTrue;

@Ignore
public class UnixSocketP2PTransportChannelIT {

    private String testSocketDir = "akka-p2p-test-sockets";

    private ActorSystem createActorSystem(String systemName) {
        Config config = ActorSystemConfigFactory.createTestActorSystemConfig(
                ConfigFactory.load(),
                Map.of(
                        "akka.p2p.hostsystem.shared-dir", testSocketDir,
                        "akka.p2p.enabled-channels", "[\"city.sane.akka.p2p.transport.direct.UnixSocketP2PTransportChannel\"]"
                )
        );
        return ActorSystem.create(systemName, config);
    }

    @Before
    public void setUp() {
        UnixSocketTransportChannelProperties props = new UnixSocketTransportChannelProperties(testSocketDir);
        File ff = new File(props.getSharedDir());
        if (ff.isDirectory()) {
            // clean directory before test
            for (File file : Objects.requireNonNull(ff.listFiles())) {
                file.delete();
            }
        }
        ff.delete();
    }

    @After
    public void tearDown() {

    }

    // Messages to an ActorSelection should arrive
    @Test(timeout = 10 * 1000L)
    public void remoteActorSelection() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system2 = createActorSystem("ChatServer2");
            system1 = createActorSystem("ChatServer1");
            // wait for peer connection
            Thread.sleep(1000L);

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
    @Ignore // ALL is disabled
    @Test(timeout = 20 * 1000L)
    public void remoteActorSelectionAll() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        ActorSystem system3 = null;
        try {
            // create connected graph of actor system peers
            system3 = createActorSystem("ChatServer3");
            Thread.sleep(500L);
            system2 = createActorSystem("ChatServer2");
            Thread.sleep(500L);
            system1 = createActorSystem( "ChatServer1");
            // wait for peer connection
            Thread.sleep(1000L);

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
    @Ignore
    @Test(timeout = 20 * 1000L)
    public void remoteActorSelectionAny() throws InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        ActorSystem system3 = null;
        try {
            // create connected graph of actor system peers
            system3 = createActorSystem("ChatServer3");
            Thread.sleep(500L);
            system2 = createActorSystem("ChatServer2");
            Thread.sleep(500L);
            system1 = createActorSystem( "ChatServer1");
            // wait for peer connection
            Thread.sleep(1000L);

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
            system2 = createActorSystem("ChatServer2");
            Thread.sleep(500L);
            system1 = createActorSystem("ChatServer1");
            // wait for peer connection
            Thread.sleep(1000L);

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


    // Throw an exception if no connection to the relay is possible
    @Test(timeout = 20 * 1000L, expected = P2PTransportException.class)
    public void testPeerTerminated() throws Throwable {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = createActorSystem( "ChatServer1");
            Thread.sleep(500L);
            system2 = createActorSystem( "ChatServer2");
            // wait for peer connection
            Thread.sleep(1000L);

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

            // send message from alice to bob using an ActorRef
            ActorSelection selectionBob = system1.actorSelection("bud://ChatServer2/user/Bob");
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();

            TestKit.shutdownActorSystem(system1);

            // wait for client to become aware of offline peer
            Thread.sleep(2 * 1000L);

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

}
