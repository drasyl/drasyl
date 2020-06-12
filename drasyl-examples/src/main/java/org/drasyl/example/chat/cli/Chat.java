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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.identity.Address;
import org.drasyl.messenger.MessengerException;
import org.drasyl.util.Pair;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
@SuppressWarnings("squid:S106")
public class Chat {
    public static void main(String[] args) throws DrasylException {
        Config config;
        if (args.length == 1) {
            config = ConfigFactory.parseFile(new File(args[0])).withFallback(ConfigFactory.load());
        }
        else {
            config = ConfigFactory.load();
        }

        CompletableFuture<Void> online = new CompletableFuture<>();
        DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                switch (event.getType()) {
                    case EVENT_MESSAGE:
                        Pair<Address, byte[]> message = event.getMessage();
                        System.out.println("From " + message.first() + ": " + new String(message.second()));
                        break;
                    case EVENT_NODE_ONLINE:
                        online.complete(null);
                        System.out.println("Online! Your Address is: " + event.getNode().getIdentity().getAddress());
                        break;
                    case EVENT_NODE_OFFLINE:
                        System.out.println("Offline! No messages can be sent at the moment. Wait until node comes back online.");
                        break;
                    case EVENT_NODE_UP:
                    case EVENT_NODE_DOWN:
                        // ignore
                        break;
                    default:
                        System.out.println(event);
                }
            }
        };
        node.start().join();
        online.join();

        System.out.println();
        System.out.println("****************************************************************************************");
        System.out.println("This is an Example of a Chat Application running on the drasyl Overlay Network.");
        System.out.println("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.");
        System.out.println("****************************************************************************************");
        System.out.println();

        String recipient = "";
        Scanner scanner = new Scanner(System.in);
        AtomicBoolean keepRunning = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> keepRunning.set(false)));
        while (keepRunning.get()) {
            try {
                // prompt for recipient
                System.out.print("Recipient [" + recipient + "]? ");
                String newRecipient = scanner.nextLine();
                if (!newRecipient.isBlank()) {
                    recipient = newRecipient;
                }
                if (recipient.isBlank()) {
                    System.err.println("You must specify a recipient.");
                    continue;
                }

                // prompt for message
                System.out.print("Message? ");
                String message = scanner.nextLine();

                try {
                    node.send(recipient, message);
                    System.out.println("To " + recipient + ": " + message);
                }
                catch (MessengerException e) {
                    System.err.println("Unable to sent message: " + e.getMessage());
                }
            }
            catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }

        node.shutdown().join();
    }
}
