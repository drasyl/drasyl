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
package org.drasyl.example.qotm;

import org.drasyl.annotation.NonNull;
import org.drasyl.example.qotm.QuoteOfTheMomentServer.Quote;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({ "java:S106", "java:S1845", "java:S2096" })
public class QuoteOfTheMomentClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "qotm-client.identity");
    private static final Duration pullTimeout = Duration.ofSeconds(3);
    private final CompletableFuture<Void> online = new CompletableFuture<>();

    public QuoteOfTheMomentClient() throws DrasylException {
        super(DrasylConfig.newBuilder()
                .addSerializationsBindingsInbound(Quote.class, "jackson-json")
                .identityPath(Path.of(IDENTITY))
                .build());
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        }

        if (event instanceof MessageEvent) {
            final MessageEvent msg = (MessageEvent) event;

            if (msg.getPayload() instanceof Quote) {
                final Quote quote = (Quote) msg.getPayload();

                System.out.println(quote);
            }
        }
    }

    public CompletableFuture<Void> online() {
        return online;
    }

    public static void main(final String[] args) throws DrasylException {
        if (args.length != 1) {
            System.err.println("Please provide QuoteOfTheMomentServer address as first argument.");
            System.exit(1);
        }
        final String recipient = args[0];

        final QuoteOfTheMomentClient node = new QuoteOfTheMomentClient();
        node.start().join();
        node.online().join();

        // ask for next quote periodically every n seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                node.send(recipient, null);
            }
        }, 0L, pullTimeout.toMillis());
    }
}
