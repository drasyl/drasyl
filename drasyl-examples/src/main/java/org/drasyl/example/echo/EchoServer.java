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

import java.nio.file.Path;

/**
 * Starts a node that sends all received messages back to the receiver. Based on the <a
 * href="https://tools.ietf.org/html/rfc862">Echo Protocol</a>.
 *
 * @see EchoClient
 */
@SuppressWarnings({ "java:S106", "java:S125", "java:S112", "java:S2096" })
public class EchoServer extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "echo-server.identity");

    protected EchoServer(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof MessageEvent) {
            System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `" + ((MessageEvent) event).getSender() + "`");
            send(((MessageEvent) event).getSender(), ((MessageEvent) event).getPayload()).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
        }
    }

    public static void main(final String[] args) throws DrasylException {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final EchoServer node = new EchoServer(config);

        node.start().toCompletableFuture().join();
        System.out.println("EchoServer listening on address " + node.identity().getIdentityPublicKey());
    }
}
