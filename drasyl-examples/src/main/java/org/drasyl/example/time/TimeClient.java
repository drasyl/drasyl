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
package org.drasyl.example.time;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.drasyl.example.time.TimeServer.UNIX_TIME_OFFSET;

/**
 * Starts a node which sends an empty message to the given address. Expects a reply with the time in
 * seconds since midnight on January first 1900. Based on the <a href="https://tools.ietf.org/html/rfc868">Time
 * Protocol</a>.
 *
 * @see TimeServer
 */
@SuppressWarnings({
        "java:S106",
        "java:S112",
        "java:S125",
        "java:S126",
        "java:S2096",
        "java:S2143"
})
public class TimeClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "time-client.identity.json");
    private final CompletableFuture<Void> online = new CompletableFuture<>();

    protected TimeClient(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        }
        else if (event instanceof MessageEvent && ((MessageEvent) event).getPayload() instanceof Integer) {
            final long payload = Integer.toUnsignedLong((int) ((MessageEvent) event).getPayload());
            final long currentTimeMillis = (payload - UNIX_TIME_OFFSET) * 1000L;
            System.out.println(new Date(currentTimeMillis));
            shutdown().thenRun(() -> System.exit(0));
        }
    }

    public static void main(final String[] args) throws ExecutionException, InterruptedException, DrasylException {
        if (args.length != 1) {
            System.err.println("Please provide TimeServer address as first argument.");
            System.exit(1);
        }
        final String recipient = args[0];

        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final TimeClient node = new TimeClient(config);

        node.start().get();
        node.online.join();
        System.out.println("TimeClient started");

        node.send(recipient, null).exceptionally(e -> {
            throw new RuntimeException("Unable to process message.", e);
        });
    }
}
