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

import org.drasyl.util.internal.NonNull;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;

import java.nio.file.Path;

/**
 * Starts a node which discards all incoming messages. Based on the <a
 * href="https://tools.ietf.org/html/rfc863">Discard Protocol</a>.
 *
 * @see DiscardClient
 */
@SuppressWarnings({
        "java:S106",
        "java:S112",
        "java:S125",
        "java:S1845",
        "java:S2096",
        "java:S2189",
        "InfiniteLoopStatement",
        "StatementWithEmptyBody"
})
public class DiscardServer extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "discard-server.identity");

    protected DiscardServer(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof MessageEvent) {
            // discard message
            System.out.println("Got `" + ((MessageEvent) event).getPayload() + "` from `" + ((MessageEvent) event).getSender() + "`");
        }
    }

    public static void main(final String[] args) throws DrasylException {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final DiscardServer node = new DiscardServer(config);

        node.start().toCompletableFuture().join();
        System.out.println("DiscardServer listening on address " + node.identity().getAddress());

        while (true) {
            // node should run forever
        }
    }
}
