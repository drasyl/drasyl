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
package org.drasyl.example.echo;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Starts a node that sends all received messages back to the receiver. Based on the <a
 * href="https://tools.ietf.org/html/rfc862">Echo Protocol</a>.
 *
 * @see EchoClient
 */
@SuppressWarnings({ "java:S106", "java:S125", "java:S112", "java:S2096" })
public class EchoServer extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "echo-server.identity.json");

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

    public static void main(final String[] args) throws DrasylException, ExecutionException, InterruptedException {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final EchoServer node = new EchoServer(config);

        node.start().get();
        System.out.println("EchoServer listening on address " + node.identity().getPublicKey());
    }
}
