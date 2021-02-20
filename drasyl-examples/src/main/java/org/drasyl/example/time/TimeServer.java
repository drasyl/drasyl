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

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Starts a node which replies the time in seconds since midnight on January first 1900, when
 * receiving an empty message. Based on the <a href="https://tools.ietf.org/html/rfc868">Time
 * Protocol</a>.
 *
 * @see TimeClient
 */
@SuppressWarnings({ "java:S106", "java:S112", "java:S125", "java:S2096" })
public class TimeServer extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "time-server.identity.json");
    public static final long UNIX_TIME_OFFSET = 2_208_988_800L;

    protected TimeServer(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof MessageEvent && ((MessageEvent) event).getPayload() == null) {
            final int currentTime = (int) (System.currentTimeMillis() / 1_000L + UNIX_TIME_OFFSET);
            send(((MessageEvent) event).getSender(), currentTime).exceptionally(e -> {
                throw new RuntimeException("Unable to process message.", e);
            });
        }
    }

    public static void main(final String[] args) throws DrasylException, ExecutionException, InterruptedException {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final TimeServer node = new TimeServer(config);

        node.start().get();
        System.out.println("TimeServer listening on address " + node.identity().getPublicKey());
    }
}
