/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.cli.command.perf;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.cli.command.perf.message.PerfMessage;
import org.drasyl.cli.command.perf.message.SessionConfirmation;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.drasyl.behaviour.Behaviors.same;
import static org.drasyl.serialization.Serializers.SERIALIZER_JACKSON_JSON;

/**
 * {@link PerfClientNode}s can connect to this server to perform connection tests.
 *
 * <p>The server runs continuously. Clients can request an exclusive session on the server and do a
 * performance test during that session. Once the test is complete, the session is closed, and the
 * server waits for the next session.
 */
public class PerfServerNode extends BehavioralDrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(PerfServerNode.class);
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    public static final Duration TEST_DELAY = ofMillis(10);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final Scheduler perfScheduler;

    @SuppressWarnings("java:S107")
    PerfServerNode(final CompletableFuture<Void> doneFuture,
                   final PrintStream printStream,
                   final Scheduler perfScheduler,
                   final DrasylConfig config,
                   final Identity identity,
                   final PeersManager peersManager,
                   final Pipeline pipeline,
                   final PluginManager pluginManager,
                   final AtomicReference<CompletableFuture<Void>> startFuture,
                   final AtomicReference<CompletableFuture<Void>> shutdownFuture,
                   final Scheduler scheduler) {
        super(config, identity, peersManager, pipeline, pluginManager, startFuture, shutdownFuture, scheduler);
        this.doneFuture = doneFuture;
        this.printStream = printStream;
        this.perfScheduler = perfScheduler;
    }

    public PerfServerNode(final DrasylConfig config,
                          final PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .addSerializationsBindingsInbound(PerfMessage.class, SERIALIZER_JACKSON_JSON)
                .addSerializationsBindingsOutbound(PerfMessage.class, SERIALIZER_JACKSON_JSON)
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        perfScheduler = Schedulers.io();
    }

    @Override
    protected Behavior created() {
        return offline();
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    /**
     * Node is not connected to super peer (node must be online to perform a performance test).
     */
    private Behavior offline() {
        return Behaviors.receive()
                .onEvent(NodeUpEvent.class, event -> Behaviors.withScheduler(eventScheduler -> {
                    eventScheduler.scheduleEvent(new OnlineTimeout(), ONLINE_TIMEOUT);
                    return offline();
                }))
                .onEvent(NodeUnrecoverableErrorEvent.class, event -> {
                    doneFuture.completeExceptionally(event.getError());
                    return Behaviors.ignore();
                })
                .onEvent(NodeNormalTerminationEvent.class, event -> {
                    doneFuture.complete(null);
                    return Behaviors.ignore();
                })
                .onEvent(NodeOnlineEvent.class, event -> waitForSession())
                .onEvent(OnlineTimeout.class, event -> {
                    doneFuture.completeExceptionally(new Exception("Server did not come online within " + ONLINE_TIMEOUT.toSeconds() + "s. Look like super peer is unavailable."));
                    return Behaviors.ignore();
                })
                .onAnyEvent(event -> same())
                .build();
    }

    /**
     * Node is waiting for (new) sessions.
     */
    private Behavior waitForSession() {
        printStream.println("----------------------------------------------------------------------------------------------");
        printStream.println("Server listening on address " + identity().getPublicKey());
        printStream.println("----------------------------------------------------------------------------------------------");

        // new behavior
        return Behaviors.receive()
                .onMessage(SessionRequest.class, (sender, payload) -> {
                    LOG.debug("Got session request from {}", sender);

                    // confirm session
                    LOG.debug("Confirm session request from {}", sender);
                    printStream.println("Accepted session from " + sender);
                    send(sender, new SessionConfirmation());

                    if (payload.isReverse()) {
                        return startTest(sender, payload);
                    }
                    else {
                        // in reverse mode, a race condition can occur between the session
                        // confirmation message and the performance test messages. although the
                        // latter are sent later, they can arrive earlier at the receiver because
                        // they are sent as byte arrays and thus do not have to go through the
                        // (de)serialization process.
                        // This is a workaround by simply delaying the test a bit and thus
                        // significantly reducing (not eliminating) the risk of a race condition.
                        return startTestDelayed(sender, payload);
                    }
                })
                .onEvent(NodeOfflineEvent.class, event -> {
                    printStream.println("Lost connection to super peer. Wait until server is back online...");
                    return offline();
                })
                .onAnyEvent(event -> same())
                .build();
    }

    /**
     * Node performs session and wait for completion.
     */
    private Behavior startTest(final CompressedPublicKey client,
                               final SessionRequest session) {
        if (!session.isReverse()) {
            LOG.debug("Start sender.");
            final PerfTestReceiver receiver = new PerfTestReceiver(client, session, perfScheduler, printStream, this::send, this::waitForSession, e -> waitForSession());
            return receiver.run();
        }
        else {
            LOG.debug("Running in reverse mode. Start receiver.");
            final PerfTestSender sender = new PerfTestSender(client, session, perfScheduler, printStream, this::send, this::waitForSession, e -> waitForSession());
            return sender.run();
        }
    }

    /**
     * Node performs test after waiting for {@code duration}.
     */
    private Behavior startTestDelayed(final CompressedPublicKey client,
                                      final SessionRequest session) {
        return Behaviors.withScheduler(scheduler -> {
            scheduler.scheduleEvent(new TestDelayed(), TEST_DELAY);

            return Behaviors.receive()
                    .onEvent(TestDelayed.class, e -> startTest(client, session))
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    /**
     * Signals that the start of the test has been delayed long enough.
     */
    static class TestDelayed implements Event {
    }

    /**
     * Signals that the server could not go online.
     */
    static class OnlineTimeout implements Event {
    }
}
