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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.annotation.NonNull;
import org.drasyl.cli.command.perf.message.SessionConfirmation;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.behaviour.Behavior;
import org.drasyl.node.behaviour.BehavioralDrasylNode;
import org.drasyl.node.behaviour.Behaviors;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.drasyl.node.behaviour.Behaviors.ignore;
import static org.drasyl.node.behaviour.Behaviors.same;

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
    private final EventLoopGroup eventLoopGroup;

    @SuppressWarnings("java:S107")
    PerfServerNode(final CompletableFuture<Void> doneFuture,
                   final PrintStream printStream,
                   final EventLoopGroup eventLoopGroup,
                   final Identity identity,
                   final ServerBootstrap bootstrap,
                   final ChannelFuture channelFuture,
                   final ChannelGroup channels) {
        super(identity, bootstrap, channelFuture, channels);
        this.doneFuture = doneFuture;
        this.printStream = printStream;
        this.eventLoopGroup = eventLoopGroup;
    }

    public PerfServerNode(final DrasylConfig config,
                          final PrintStream printStream) throws DrasylException {
        super(config);
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        eventLoopGroup = new NioEventLoopGroup(1);
        // use special channel initializer
        bootstrap.childHandler(new PerfChannelInitializer(this, config));
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
        return newBehaviorBuilder()
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
        printStream.println("Server listening on address " + identity().getIdentityPublicKey());
        printStream.println("----------------------------------------------------------------------------------------------");

        // new behavior
        return newBehaviorBuilder()
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
                .onEvent(InboundExceptionEvent.class, event -> {
                    LOG.warn("Inbound exception:", event::getError);
                    return Behaviors.same();
                })
                .onAnyEvent(event -> same())
                .build();
    }

    /**
     * Node performs session and wait for completion.
     */
    @SuppressWarnings("java:S1142")
    private Behavior startTest(final DrasylAddress client,
                               final SessionRequest session) {
        try {
            final Channel channel = resolve(client).toCompletableFuture().get();

            if (!session.isReverse()) {
                LOG.debug("Start sender.");
                final PerfTestReceiver receiver = new PerfTestReceiver(client, session, eventLoopGroup, printStream, this::send, this::waitForSession, e -> waitForSession());
                return receiver.run();
            }
            else {
                LOG.debug("Running in reverse mode. Start receiver.");
                final PerfTestSender sender = new PerfTestSender(session, eventLoopGroup, printStream, channel, this::waitForSession, e -> waitForSession());
                return sender.run();
            }
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return ignore();
        }
        catch (final ExecutionException e) {
            doneFuture.completeExceptionally(e);
            return ignore();
        }
    }

    /**
     * Node performs test after waiting for {@code duration}.
     */
    private Behavior startTestDelayed(final DrasylAddress client,
                                      final SessionRequest session) {
        return Behaviors.withScheduler(scheduler -> {
            scheduler.scheduleEvent(new TestDelayed(), TEST_DELAY);

            return newBehaviorBuilder()
                    .onEvent(TestDelayed.class, e -> startTest(client, session))
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    @NonNull
    @Override
    public synchronized CompletableFuture<Void> shutdown() {
        try {
            return super.shutdown();
        }
        finally {
            eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
        }
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
