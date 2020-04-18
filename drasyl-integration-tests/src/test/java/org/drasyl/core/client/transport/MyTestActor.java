package org.drasyl.core.client.transport;

import akka.actor.AbstractActor;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class MyTestActor extends AbstractActor {
    private final CompletableFuture<Object> messageReceived;

    public MyTestActor(CompletableFuture<Object> messageReceived) {
        this.messageReceived = messageReceived;
    }

    public Receive createReceive() {
        return receiveBuilder().match(Object.class, this::messageReceived).build();
    }

    private void messageReceived(Object message) {
        messageReceived.complete(message);
    }

    public static Props probs(CompletableFuture<Object> messageReceivedAlice) {
        return Props.create(MyTestActor.class, () -> new MyTestActor(messageReceivedAlice));
    }

    public static <E extends Throwable> void testSystems(Function<String, ActorSystem> createSystem, FailingConsumer<ActorSystem[], E> consumer, String ... systemNames) throws E {
        ActorSystem[] systems = Arrays.stream(systemNames)
                .map(createSystem)
                .toArray(ActorSystem[]::new);
        try {
            consumer.accept(systems);
        } finally {
            for (ActorSystem system : systems) {
                TestKit.shutdownActorSystem(system);
            }
        }
    }

    private static <E extends Throwable> void setupAliceAndBob(ActorSystem[] systems, AliceBobConsumer<E> consumer) throws E {
        CompletableFuture<Object> messageReceivedAlice = new CompletableFuture<>();
        CompletableFuture<Object> messageReceivedBob = new CompletableFuture<>();

        systems[0].actorOf(MyTestActor.probs(messageReceivedAlice), "Alice");
        systems[1].actorOf(MyTestActor.probs(messageReceivedBob), "Bob");

        ActorSelection selectionBob = systems[0].actorSelection("bud://" + systems[1].name() + "/user/Bob");
        ActorSelection selectionAlice = systems[1].actorSelection("bud://" + systems[0].name() + "/user/Alice");

        consumer.accept(systems, selectionAlice, selectionBob, messageReceivedAlice, messageReceivedBob);
    }

    public static <E extends Throwable> void aliceBobSystems(Function<String, ActorSystem> createSystem,
                                                            AliceBobConsumer<E> aliceBobConsumer) throws  E{
        testSystems(createSystem, systems -> setupAliceAndBob(systems, aliceBobConsumer),
                "TestSystem1", "TestSystem2");
    }

    @FunctionalInterface
    public interface FailingConsumer<T, E extends Throwable> {
        void accept(T obj) throws E;
    }

    @FunctionalInterface
    public interface AliceBobConsumer<E extends Throwable> {
        void accept(ActorSystem[] systems,
                    ActorSelection selectionAlice,
                    ActorSelection selectionBob,
                    CompletableFuture<Object> messageReceivedAlice,
                    CompletableFuture<Object> messageReceivedBob) throws E;
    }


}
