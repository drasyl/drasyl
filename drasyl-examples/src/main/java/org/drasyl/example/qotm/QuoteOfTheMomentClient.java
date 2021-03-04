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
package org.drasyl.example.qotm;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.example.qotm.QuoteOfTheMomentServer.Quote;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({ "java:S106", "java:S2096" })
public class QuoteOfTheMomentClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "qotm-client.identity.json");
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

        //ask for next quote periodically every n seconds
        DrasylSchedulerUtil.getInstanceLight().schedulePeriodicallyDirect(
                () -> node.send(recipient, null),
                0, pullTimeout.toSeconds(), TimeUnit.SECONDS);
    }
}
