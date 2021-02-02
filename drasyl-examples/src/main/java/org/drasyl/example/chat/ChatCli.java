/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.example.chat;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

import java.io.File;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.Duration.ofSeconds;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
@SuppressWarnings({ "squid:S106", "java:S1141", "java:S3776" })
public class ChatCli {
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    private static final Scanner scanner = new Scanner(System.in);
    private static String prompt;

    public static void main(final String[] args) throws DrasylException {
        final DrasylConfig config;
        if (args.length == 1) {
            config = DrasylConfig.parseFile(new File(args[0]));
        }
        else {
            config = new DrasylConfig();
        }

        final DrasylNode node = new BehavioralDrasylNode(config) {
            @Override
            protected Behavior created() {
                return offline();
            }

            /**
             * Node is not connected to super peer.
             */
            private Behavior offline() {
                return Behaviors.receive()
                        .onEvent(NodeUpEvent.class, event -> {
                            System.out.println("drasyl Node started. Connecting to super peer...");
                            return Behaviors.withScheduler(scheduler -> {
                                scheduler.scheduleEvent(new OnlineTimeout(), ONLINE_TIMEOUT);
                                return offline();
                            });
                        })
                        .onEvent(NodeUnrecoverableErrorEvent.class, event -> {
                            System.err.println("drasyl Node encountered an unrecoverable error: " + event.getError().getMessage());
                            return Behaviors.shutdown();
                        })
                        .onEvent(NodeNormalTerminationEvent.class, this::terminationEvent)
                        .onEvent(NodeDownEvent.class, this::downEvent)
                        .onEvent(NodeOnlineEvent.class, event -> {
                            addBeforePrompt("drasyl Node is connected to super peer. Relayed communication and discovery available.");
                            return online();
                        })
                        .onEvent(OnlineTimeout.class, event -> {
                            addBeforePrompt("No response from the Super Peer within " + ONLINE_TIMEOUT.toSeconds() + "s. Probably the Super Peer is unavailable or your configuration is faulty.");
                            return Behaviors.same();
                        })
                        .onEvent(PeerEvent.class, this::peerEvent)
                        .onMessage(String.class, this::messageEvent)
                        .onAnyEvent(event -> Behaviors.same())
                        .build();
            }

            /**
             * Node is connected to super peer.
             */
            private Behavior online() {
                return Behaviors.receive()
                        .onEvent(NodeNormalTerminationEvent.class, this::terminationEvent)
                        .onEvent(NodeDownEvent.class, this::downEvent)
                        .onEvent(NodeOfflineEvent.class, event -> {
                            addBeforePrompt("drasyl Node lost connection to super peer. Relayed communication and discovery not available.");
                            return offline();
                        })
                        .onEvent(PeerEvent.class, this::peerEvent)
                        .onMessage(String.class, this::messageEvent)
                        .onAnyEvent(event -> Behaviors.same())
                        .build();
            }

            /**
             * Reaction to a {@link NodeDownEvent}.
             */
            private Behavior downEvent(final NodeDownEvent event) {
                System.err.append("drasyl Node is shutting down. No more communication possible.\n");
                return Behaviors.same();
            }

            /**
             * Reaction to a {@link NodeNormalTerminationEvent}.
             */
            private Behavior terminationEvent(final NodeNormalTerminationEvent event) {
                System.err.append("drasyl Node has been shut down.\n");
                return Behaviors.ignore();
            }

            /**
             * Reaction to a {@link org.drasyl.event.MessageEvent}.
             */
            private Behavior messageEvent(final CompressedPublicKey sender, final String payload) {
                addBeforePrompt("From " + sender + ": " + payload);
                return Behaviors.same();
            }

            /**
             * Reaction to a {@link PeerEvent}.
             */
            private Behavior peerEvent(final PeerEvent event) {
                if (event instanceof PeerDirectEvent) {
                    addBeforePrompt("Direct connection to " + event.getPeer().getPublicKey() + " available.");
                }
                else if (event instanceof PeerRelayEvent) {
                    addBeforePrompt("Relayed connection to " + event.getPeer().getPublicKey() + " available.");
                }
                return Behaviors.same();
            }

            /**
             * Signals that the node could not go online.
             */
            class OnlineTimeout implements Event {
            }
        };

        System.out.println();
        System.out.println("****************************************************************************************");
        System.out.println("This is an Example of a Chat Application running on the drasyl Overlay Network.");
        System.out.println("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.");
        System.out.println("Your address is " + node.identity().getPublicKey());
        System.out.println("****************************************************************************************");
        System.out.println();

        node.start().join();

        String recipient = "";
        final AtomicBoolean keepRunning = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> keepRunning.set(false)));
        while (keepRunning.get()) {
            // prompt for recipient
            final String newRecipient = newPrompt("Recipient [" + recipient + "]?");
            if (!newRecipient.isBlank()) {
                recipient = newRecipient;
            }
            if (recipient.isBlank()) {
                System.out.println("\rERR: You must specify a recipient.");
                continue;
            }

            // prompt for message
            String message = newPrompt("Message?");

            if (message.isBlank()) {
                message = "(blank)";
            }

            node.send(recipient, message).exceptionally(e -> {
                System.err.println("ERR: Unable to sent message: " + e.getMessage());
                return null;
            });
            System.out.println("To " + recipient + ": " + message);
        }

        node.shutdown().join();
        DrasylSchedulerUtil.shutdown();
    }

    static void addBeforePrompt(final Object x) {
        if (prompt == null) {
            System.out.println(x);
        }
        else {
            System.out.print("\r");
            System.out.println(x);
            System.out.print(prompt + " ");
        }
    }

    static String newPrompt(final String prompt) {
        ChatCli.prompt = prompt;
        System.out.print(prompt + " ");
        final String result = scanner.nextLine();
        ChatCli.prompt = null;
        return result;
    }
}
