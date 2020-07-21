/*
 * Copyright (c) 2020.
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
package org.drasyl.example.chat.cli;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.Pair;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
@SuppressWarnings({ "squid:S106", "java:S1141", "java:S3776" })
public class Chat {
    private static final Scanner scanner = new Scanner(System.in);
    private static String prompt;

    public static void main(String[] args) throws DrasylException {
        DrasylConfig config;
        if (args.length == 1) {
            config = DrasylConfig.parseFile(new File(args[0]));
        }
        else {
            config = new DrasylConfig();
        }

        CompletableFuture<Void> online = new CompletableFuture<>();
        DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                if (event instanceof MessageEvent) {
                    Pair<CompressedPublicKey, Object> message = ((MessageEvent) event).getMessage();
                    if (message.second() instanceof String) {
                        addBeforePrompt("From " + message.first() + ": " + message.second());
                    }
                }
                else if (event instanceof NodeOnlineEvent) {
                    if (online.isDone()) {
                        addBeforePrompt("Back online! Now messages can be sent again.");
                    }
                    else {
                        addBeforePrompt("Online! Your Public Key (address) is: " + ((NodeEvent) event).getNode().getIdentity().getPublicKey());
                        online.complete(null);
                    }
                }
                else if (event instanceof NodeOfflineEvent) {
                    addBeforePrompt("Offline! No messages can be sent at the moment. Wait until node comes back online.");
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
        System.out.println("****************************************************************************************");
        System.out.println();

        System.out.println("Connect to drasyl Overlay Network...");
        node.start().join();
        online.join();

        String recipient = "";
        AtomicBoolean keepRunning = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> keepRunning.set(false)));
        while (keepRunning.get()) {
            // prompt for recipient
            String newRecipient = newPrompt("Recipient [" + recipient + "]?");
            if (!newRecipient.isBlank()) {
                recipient = newRecipient;
            }
            if (recipient.isBlank()) {
                System.out.println("\rERR: You must specify a recipient.");
                continue;
            }

            // prompt for message
            String message = newPrompt("Message?");

            try {
                if (message.isBlank()) {
                    message = "(blank)";
                }

                node.send(recipient, message);
                System.out.println("To " + recipient + ": " + message);
            }
            catch (DrasylException e) {
                System.out.println("ERR: Unable to sent message: " + e.getMessage());
            }
        }

        node.shutdown().join();
        DrasylScheduler.shutdown();
    }

    static void addBeforePrompt(Object x) {
        if (prompt == null) {
            System.out.println(x);
        }
        else {
            System.out.print("\r");
            System.out.println(x);
            System.out.print(prompt + " ");
        }
    }

    static String newPrompt(String prompt) {
        Chat.prompt = prompt;
        System.out.print(prompt + " ");
        String result = scanner.nextLine();
        Chat.prompt = null;
        return result;
    }
}
