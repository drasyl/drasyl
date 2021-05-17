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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.behaviour.Behaviors.ignore;
import static org.drasyl.behaviour.Behaviors.same;
import static org.drasyl.serialization.Serializers.SERIALIZER_JACKSON_JSON;
import static org.drasyl.util.SecretUtil.maskSecret;

@SuppressWarnings({ "java:S107" })
public class SendingWormholeNode extends BehavioralDrasylNode {
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    public static final int PASSWORD_LENGTH = 16;
    private static final Logger LOG = LoggerFactory.getLogger(SendingWormholeNode.class);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream out;
    private final String password;
    private String text;

    @SuppressWarnings("SameParameterValue")
    SendingWormholeNode(final CompletableFuture<Void> doneFuture,
                        final PrintStream out,
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
        this.doneFuture = requireNonNull(doneFuture);
        this.out = requireNonNull(out);
        this.password = password;
    }

    public SendingWormholeNode(final DrasylConfig config,
                               final PrintStream out) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .addSerializationsBindingsInbound(WormholeMessage.class, SERIALIZER_JACKSON_JSON)
                .addSerializationsBindingsOutbound(WormholeMessage.class, SERIALIZER_JACKSON_JSON)
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.out = requireNonNull(out);
        this.password = Crypto.randomString(PASSWORD_LENGTH);
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
                        return ignore();
                    })
                    .onEvent(NodeNormalTerminationEvent.class, event -> {
                        doneFuture.complete(null);
                        return ignore();
                    })
                    .onEvent(NodeOnlineEvent.class, event -> online())
                    .onEvent(OnlineTimeout.class, event -> {
                        doneFuture.completeExceptionally(new Exception("Node did not come online within " + ONLINE_TIMEOUT.toSeconds() + "s. Look like super peer is unavailable."));
                        return ignore();
                    })
                    .onEvent(SetText.class, event -> text == null, event -> {
                        // text has been set -> wait for node to become online
                        this.text = event.text;
                        return offline();
                    })
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    /**
     * Node is connected to super peer.
     */
    private Behavior online() {
        if (this.text != null) {
            final String code = identity().getIdentityPublicKey().toString() + password;
            out.println("Wormhole code is: " + code);
            out.println("On the other computer, please run:");
            out.println();
            out.println("drasyl wormhole receive " + code);
            out.println();
        }

        return Behaviors.receive()
                .onEvent(NodeNormalTerminationEvent.class, event -> {
                    doneFuture.complete(null);
                    return ignore();
                })
                .onEvent(NodeOfflineEvent.class, event -> offline())
                .onEvent(SetText.class, event -> text == null, event -> {
                    // text has been set
                    this.text = event.text;
                    return online();
                })
                .onMessage(PasswordMessage.class, (sender, payload) -> text != null && password.equals(payload.getPassword()), (sender, payload) -> {
                    // correct password -> send text
                    LOG.debug("Got text request from '{}' with correct password '{}'. Reply with text.", () -> sender, () -> maskSecret(payload.getPassword()));

                    send(sender, new TextMessage(text)).whenComplete((result, e) -> {
                        if (e == null) {
                            out.println("text message sent");
                            doneFuture.complete(null);
                        }
                        else {
                            doneFuture.completeExceptionally(e);
                        }
                    });
                    return ignore();
                })
                .onMessage(PasswordMessage.class, (sender, payload) -> {
                    // wrong password -> send rejection
                    send(sender, new WrongPasswordMessage()).whenComplete((result, e) -> doneFuture.completeExceptionally(new Exception(
                            "Code confirmation failed. Either you or your correspondent\n" +
                                    "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                                    "incorrectly. You could try again, giving both your correspondent and\n" +
                                    "the attacker another chance."
                    )));
                    return ignore();
                })
                .onAnyEvent(event -> same())
                .build();
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
