package org.drasyl.core.client.transport.relay;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.Route;
import akka.testkit.javadsl.TestKit;
import org.drasyl.core.client.transport.relay.handler.RelayP2PTransportChannelInitializer;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * The tests in this class should support the development of the {@link RelayP2PTransportChannelInitializer}. With these tests the behaviour of the
 * {@link akka.remote.RemoteActorRefProvider} can be examined, so that this behaviour can be easier migrated to the
 * {@link RelayP2PTransportChannelInitializer}.
 */
//FIXME
@Ignore("Must be repaired")
public class RemoteIT {
    // Messages to an ActorRef resolved from an ActorSelection should arrive
    @Test(timeout = 1000 * 1000L)
    public void resolvedActorRef() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = ActorSystem.create("ChatServer1", ConfigFactory.parseString("akka.actor.provider = remote\nakka.remote.netty.tcp.hostname = 127.0.0.1\nakka.remote.netty.tcp.port = 2551").withFallback(ConfigFactory.load()));
            system2 = ActorSystem.create("ChatServer2", ConfigFactory.parseString("akka.actor.provider = remote\nakka.remote.netty.tcp.hostname = 127.0.0.1\nakka.remote.netty.tcp.port = 2552").withFallback(ConfigFactory.load()));

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyActor.probs(messageReceivedBob), "Bob");

            // sendMSG message from alice to bob using an ActorRef
            ActorSelection selectionBob = system1.actorSelection("akka.tcp://ChatServer2@127.0.0.1:2552/user/Bob");
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(100)).toCompletableFuture().get();
            actorRefBob.tell("Hallo Bob!", actorRefAlice);
            //
            Assert.assertEquals("Hallo Bob!", messageReceivedBob.get());
        }
        finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
            if (system2 != null) {
                TestKit.shutdownActorSystem(system2);
            }
        }
    }

    @Test
    public void sendActorRef() throws ExecutionException, InterruptedException {
        ActorSystem system1 = null;
        ActorSystem system2 = null;
        try {
            // create two actor systems with one actor on each system
            system1 = ActorSystem.create("ChatServer1", ConfigFactory.parseString("akka.actor.provider = remote\nakka.remote.netty.tcp.hostname = 127.0.0.1\nakka.remote.netty.tcp.port = 2551").withFallback(ConfigFactory.load()));
            system2 = ActorSystem.create("ChatServer2", ConfigFactory.parseString("akka.actor.provider = remote\nakka.remote.netty.tcp.hostname = 127.0.0.1\nakka.remote.netty.tcp.port = 2552").withFallback(ConfigFactory.load()));

            CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
            ActorRef actorRefAlice = system1.actorOf(MyActor.probs(messageReceivedAlice), "Alice");
            CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();
            system2.actorOf(MyActor.probs(messageReceivedBob), "Bob");

            // sendMSG message from alice to bob using an ActorRef
            ActorSelection selectionBob = system1.actorSelection("akka.tcp://ChatServer2@127.0.0.1:2552/user/Bob");
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(10)).toCompletableFuture().get();
            actorRefBob.tell(actorRefBob, actorRefAlice);

            Assert.assertEquals(actorRefBob, messageReceivedBob.get());
        }
        finally {
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
            system1 = ActorSystem.create("ChatServer1", ConfigFactory.parseString("akka.actor.provider = remote\nakka.remote.netty.tcp.hostname = 127.0.0.1\nakka.remote.netty.tcp.port = 2552").withFallback(ConfigFactory.load()));

            MyHttpApp server = new MyHttpApp();
            server.startServer("localhost", 8080, system1);

            while (true) {
                Thread.sleep(1 * 1000L);
            }
        }
        finally {
            if (system1 != null) {
                TestKit.shutdownActorSystem(system1);
            }
        }
    }

    static class MyActor extends AbstractActor {
        private final CompletableFuture<Object> messageReceived;

        public MyActor(CompletableFuture<Object> messageReceived) {
            this.messageReceived = messageReceived;
        }

        public Receive createReceive() {
            return receiveBuilder().match(Object.class, this::messageReceived).build();
        }

        private void messageReceived(Object message) {
            messageReceived.complete(message);
        }

        public static Props probs(CompletableFuture<Object> messageReceivedAlice) {
            return Props.create(MyActor.class, () -> new MyActor(messageReceivedAlice));
        }
    }

    static class MyHttpApp extends HttpApp {
        @Override
        protected Route routes() {
            return complete("Hallo Welt");
        }
    }
}
