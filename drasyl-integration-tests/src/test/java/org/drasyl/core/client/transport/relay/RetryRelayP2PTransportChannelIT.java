package org.drasyl.core.client.transport.relay;


import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import org.drasyl.core.client.transport.ActorSystemConfigFactory;
import org.drasyl.core.client.transport.MyTestActor;
import org.drasyl.core.client.transport.P2PTransportException;
import org.drasyl.core.server.RelayServerException;
import com.typesafe.config.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class RetryRelayP2PTransportChannelIT {

    RelayHandle relay;

    private ActorSystem createActorSystem(String systemName) {
        Config config = ActorSystemConfigFactory.createTestActorSystemConfig(
                relay.getConfig(),
                Map.of(
                        "akka.p2p.enabled-channels", "[\"org.drasyl.core.client.transport.relay.RetryRelayP2PTransportChannel\"]"
                )
        );
        return ActorSystem.create(systemName, config);
    }

    private <E extends Throwable> void aliceBobSystems(MyTestActor.AliceBobConsumer<E> aliceBobConsumer) throws E {
        MyTestActor.aliceBobSystems(this::createActorSystem, aliceBobConsumer);
    }

    private void startRelay() throws RelayServerException, URISyntaxException {
        relay = new RelayHandle(2222);
        relay.start();
    }

    private void stopRelay() throws RelayServerException {
        relay.shutdown();
    }

    @Before
    public void setUp() throws RelayServerException, URISyntaxException {
        startRelay();
    }

    @After
    public void tearDown() throws RelayServerException {
        stopRelay();
    }

    // Messages to an ActorSelection should arrive
    @Test(timeout = 10 * 1000L)
    public void remoteActorSelection() throws Throwable {
        aliceBobSystems((systems, selectionAlice, selectionBob, messageReceivedAlice, messageReceivedBob) -> {

            ActorRef actorRefAlice = selectionAlice.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();
            // send message from alice to bob using an ActorSelection
            selectionBob.tell("Hallo Bob!", actorRefAlice);
            selectionAlice.tell("Hallo Alice!", ActorRef.noSender());

            Assert.assertEquals("Hallo Bob!", messageReceivedBob.get());
            Assert.assertEquals("Hallo Alice!", messageReceivedAlice.get());
        });
    }

    // Reconnect after connection loss
    @Test(timeout = 20 * 1000L)
    public void testRelayRetry() throws Throwable {
        aliceBobSystems((systems, selectionAlice, selectionBob, messageReceivedAlice, messageReceivedBob) -> {

            // send message from alice to bob using an ActorRef
            ActorRef actorRefAlice = selectionAlice.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();

            relay.shutdown();

            // testing this kills the systems (covered by other test)
//            assertThrows( RuntimeException.class, () -> {
//                actorRefBob.tell("Hallo Bob!", actorRefAlice);
//            });
//
//            assertThrows( RuntimeException.class, () -> {
//                actorRefAlice.tell("Hallo Alice!", actorRefBob);
//            });
            Thread.sleep(2000L);
            startRelay();
            Thread.sleep(5000L);
            // should have reconnected at this point -> tell should work again

            String msg1 = "Hallo again Bob!";
            String msg2 = "Hallo again Alice!";
            actorRefBob.tell(msg1, actorRefAlice);
            assertThat(messageReceivedBob.get(), equalTo(msg1));

            actorRefAlice.tell(msg2, actorRefBob);
            assertThat(messageReceivedAlice.get(), equalTo(msg2));

        });
    }

    // Throw an exception if no connection to the relay is possible (until retry maxretries reached)
    @Test(timeout = 30 * 1000L, expected = P2PTransportException.class)
    public void testRelayRetryTerminates() throws Throwable {
        aliceBobSystems((systems, selectionAlice, selectionBob, messageReceivedAlice, messageReceivedBob) -> {
            // calculate expected upper bound on retry delay
            Config sysConf = systems[0].settings().config();
            Duration retryDelay = sysConf.getDuration("akka.p2p.relay.retry-delay");
            int maxRetries = sysConf.getInt("akka.p2p.relay.max-retries");
            Duration upperBoundOnRetryDuration = retryDelay.multipliedBy(maxRetries * maxRetries);

            // send message from alice to bob using an ActorRef
            ActorRef actorRefAlice = selectionBob.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();
            ActorRef actorRefBob = selectionBob.resolveOne(Duration.ofSeconds(5)).toCompletableFuture().get();

            relay.shutdown();
            // transport should notice shutdown immediately and should not accept any messages => transport

            try {
                actorRefBob.tell("Hallo Bob!", actorRefAlice);
            } catch (Exception e) {
                throw e.getCause();
            }
        });
    }

}
