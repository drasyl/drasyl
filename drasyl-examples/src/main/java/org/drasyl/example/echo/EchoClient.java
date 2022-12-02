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
package org.drasyl.example.echo;

import org.drasyl.annotation.NonNull;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.RandomUtil.randomString;

/**
 * Starts a {@link DrasylNode} which sends one message to given address and echoes back any received message to
 * the sender. Based on the <a href="https://tools.ietf.org/html/rfc862">Echo Protocol</a>.
 *
 * @see EchoServerNode
 */
@SuppressWarnings({
        "java:S106",
        "java:S112",
        "java:S125",
        "java:S126",
        "java:S2096",
        "java:S2189",
        "InfiniteLoopStatement",
        "StatementWithEmptyBody"
})
public class EchoClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "echo-client.identity");
    private static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
    private final CompletableFuture<Void> online = new CompletableFuture<>();

    protected EchoClient(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        }
        else if (event instanceof MessageEvent) {
            System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `" + ((MessageEvent) event).getSender() + "`");
            send(((MessageEvent) event).getSender(), ((MessageEvent) event).getPayload()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
        }
    }

    public static void main(final String[] args) throws DrasylException {
        if (args.length != 1) {
            System.err.println("Please provide EchoServer address as first argument.");
            System.exit(1);
        }
        final String recipient = args[0];

        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .remoteMessageArmApplicationEnabled(false)
                .build();
        final EchoClient node = new EchoClient(config);

        node.start().toCompletableFuture().join();
        node.online.join();
        System.out.println("EchoClient started");

        final String payload = randomString(SIZE);

        System.out.println("Send `" + payload + "` to `" + recipient + "`");
        node.send(recipient, payload).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });

        while (true) {
            // node should run forever
        }
    }
}
