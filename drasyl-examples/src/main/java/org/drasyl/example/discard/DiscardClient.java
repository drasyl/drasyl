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
package org.drasyl.example.discard;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.drasyl.util.RandomUtil.randomString;

/**
 * Starts a node which keeps sending random data to given address. Based on the <a
 * href="https://tools.ietf.org/html/rfc863">Discard Protocol</a>.
 *
 * @see DiscardServer
 */
@SuppressWarnings({ "java:S106", "java:S112", "java:S2096", "StatementWithEmptyBody" })
public class DiscardClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "discard-client.identity.json");
    private static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
    private static final int PERIOD = Integer.parseInt(System.getProperty("period", "1000"));
    private final CompletableFuture<Void> online = new CompletableFuture<>();

    protected DiscardClient(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        }
        else {
            // ignore all other events
        }
    }

    public static void main(final String[] args) throws ExecutionException, InterruptedException, DrasylException {
        if (args.length != 1) {
            System.err.println("Please provide DiscardServer address as first argument.");
            System.exit(1);
        }
        final String recipient = args[0];

        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final DiscardClient node = new DiscardClient(config);

        node.start().get();
        node.online.join();
        System.out.println("DiscardClient started");

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                final String payload = randomString(SIZE);

                System.out.println("Send `" + payload + "` to `" + recipient + "`");
                node.send(recipient, payload).exceptionally(e -> {
                    throw new RuntimeException("Unable to process message.", e);
                });
            }
        }, PERIOD, PERIOD);
    }
}
