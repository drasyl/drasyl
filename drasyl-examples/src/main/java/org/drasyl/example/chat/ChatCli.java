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
package org.drasyl.example.chat;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.DrasylScheduler;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
@SuppressWarnings({ "squid:S106", "java:S1141", "java:S3776" })
public class ChatCli {
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

        final AtomicBoolean online = new AtomicBoolean();
        final DrasylNode node = new DrasylNode(config) {
            @SuppressWarnings("StatementWithEmptyBody")
            @Override
            public void onEvent(final Event event) {
                if (event instanceof MessageEvent) {
                    final CompressedPublicKey sender = ((MessageEvent) event).getSender();
                    final Object message = ((MessageEvent) event).getPayload();
                    if (message instanceof String) {
                        addBeforePrompt("From " + sender + ": " + message);
                    }
                }
                else if (event instanceof NodeOnlineEvent) {
                    addBeforePrompt("drasyl Node is connected to super peer. Relayed communication and discovery available.");
                    online.set(true);
                }
                else if (event instanceof NodeOfflineEvent) {
                    addBeforePrompt("drasyl Node lost connection to super peer. Relayed communication and discovery not available.");
                    online.set(false);
                }
                else if (event instanceof NodeUpEvent) {
                    // ignore
                }
                else {
                    addBeforePrompt(event);
                }
            }
        };

        System.out.println();
        System.out.println("****************************************************************************************");
        System.out.println("This is an Example of a Chat Application running on the drasyl Overlay Network.");
        System.out.println("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.");
        System.out.println("Your address is " + node.identity().getPublicKey());
        System.out.println("****************************************************************************************");
        System.out.println();

        System.out.println("drasyl Node started. Only communication with direct connected peers possible. Connecting to super peer...");
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
                System.out.println("ERR: Unable to sent message: " + e.getMessage());
                return null;
            });
            System.out.println("To " + recipient + ": " + message);
        }

        node.shutdown().join();
        DrasylScheduler.shutdown();
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