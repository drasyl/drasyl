/*
 * Copyright (c) 2021.
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
package org.drasyl.cli.command.wormhole;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.serialization.Serializers.SERIALIZER_JACKSON_JSON;

@SuppressWarnings({ "java:S107" })
public class SendingWormholeNode extends BehavioralDrasylNode {
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final String password;
    private String text;

    @SuppressWarnings("SameParameterValue")
    SendingWormholeNode(final CompletableFuture<Void> doneFuture,
                        final PrintStream printStream,
                        final String password,
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
        this.password = password;
    }

    public SendingWormholeNode(final DrasylConfig config,
                               final PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .addSerializationsBindingsInbound(WormholeMessage.class, SERIALIZER_JACKSON_JSON)
                .addSerializationsBindingsOutbound(WormholeMessage.class, SERIALIZER_JACKSON_JSON)
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        this.password = Crypto.randomString(16);
    }

    @Override
    protected Behavior created() {
        return offline();
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    /**
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public void setText(final String text) {
        onEvent(new SetText(text));
    }

    /**
     * Node is not connected to super peer.
     */
    private Behavior offline() {
        return Behaviors.withScheduler(scheduler -> {
            if (text != null) {
                scheduler.scheduleEvent(new OnlineTimeout(), ONLINE_TIMEOUT);
            }

            return Behaviors.receive()
                    .onEvent(NodeUnrecoverableErrorEvent.class, event -> {
                        doneFuture.completeExceptionally(event.getError());
                        return Behaviors.ignore();
                    })
                    .onEvent(NodeNormalTerminationEvent.class, event -> terminate())
                    .onEvent(NodeOnlineEvent.class, event -> online())
                    .onEvent(OnlineTimeout.class, event -> {
                        doneFuture.completeExceptionally(new Exception("Node did not come online within " + ONLINE_TIMEOUT.toSeconds() + "s. Look like super peer is unavailable."));
                        return Behaviors.ignore();
                    })
                    .onEvent(SetText.class, event -> text == null, event -> {
                        this.handleSetText(event);
                        return offline();
                    })
                    .onEvent(OnlineTimeout.class, event -> fail())
                    .onAnyEvent(event -> Behaviors.same())
                    .build();
        });
    }

    /**
     * Node is connected to super peer.
     */
    private Behavior online() {
        return Behaviors.receive()
                .onEvent(NodeNormalTerminationEvent.class, event -> terminate())
                .onEvent(NodeOfflineEvent.class, event -> offline())
                .onEvent(SetText.class, event -> text == null, event -> {
                    this.handleSetText(event);
                    return Behaviors.same();
                })
                .onMessage(PasswordMessage.class, (sender, payload) -> text != null && password.equals(payload.getPassword()), (sender, payload) -> {
                    // correct password -> send text
                    send(sender, new TextMessage(text));
                    printStream.println("text message sent");
                    return terminate();
                })
                .onMessage(PasswordMessage.class, (sender, payload) -> {
                    // wrong password -> send rejection
                    send(sender, new WrongPasswordMessage());
                    return fail();
                })
                .onAnyEvent(event -> Behaviors.same())
                .build();
    }

    /**
     * Terminate this node.
     */
    private Behavior terminate() {
        doneFuture.complete(null);
        return Behaviors.ignore();
    }

    /**
     * Transfer failed.
     */
    private Behavior fail() {
        doneFuture.completeExceptionally(new Exception(
                "Code confirmation failed. Either you or your correspondent\n" +
                        "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                        "incorrectly. You could try again, giving both your correspondent and\n" +
                        "the attacker another chance."
        ));
        return Behaviors.ignore();
    }

    /**
     * Node has received text and therefore prints the wormhole code.
     */
    private void handleSetText(final SetText event) {
        // text has been set -> display wormhole code
        this.text = event.text;
        final String code = identity().getPublicKey().toString() + password;
        printStream.println("Wormhole code is: " + code);
        printStream.println("On the other computer, please run:");
        printStream.println();
        printStream.println("drasyl wormhole receive " + code);
        printStream.println();
    }

    /**
     * Signals that the text has been set.
     */
    static class SetText implements Event {
        private final String text;

        /**
         * @throws NullPointerException if {@code text} is {@code null}
         */
        public SetText(final String text) {
            this.text = requireNonNull(text);
        }
    }

    /**
     * Signals that the node could not go online.
     */
    static class OnlineTimeout implements Event {
    }
}
