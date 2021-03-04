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
import org.drasyl.util.RandomUtil;

import java.nio.file.Path;

@SuppressWarnings({ "java:S106", "java:S2096" })
public class QuoteOfTheMomentServer extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "qotm-server.identity.json");
    private static final Quote[] QUOTES = {
            Quote.of("Stay hungry, stay foolish", "Steve Jobs"),
            Quote.of("Information is not knowledge", "Albert Einstein"),
            Quote.of("A distributed system is one where the failure of some computer I've never heard of can keep me from getting my work done", "Leslie Lamport"),
            Quote.of("Only wimps use tape backup: real men just upload their important stuff on ftp, and let the rest of the world mirror it", "Linus Torvalds"),
            Quote.of("It's harder to read code than to write it", "Joel Spolsky"),
            Quote.of("Any fool can write code that a computer can understand. Good programmers write code that humans can understand", "Martin Fowler"),
            Quote.of("Code never lies; comments sometimes do", "Ron Jeffries")
    };

    protected QuoteOfTheMomentServer() throws DrasylException {
        super(DrasylConfig.newBuilder()
                .addSerializationsBindingsOutbound(Quote.class, "jackson-json")
                .identityPath(Path.of(IDENTITY))
                .build());
    }

    @Override
    public void onEvent(@NonNull final Event event) {
        if (event instanceof MessageEvent) {
            final MessageEvent msg = (MessageEvent) event;
            this.send(msg.getSender(), getQuoteOfTheMoment());
        }
    }

    public static void main(final String[] args) throws DrasylException {
        final DrasylNode node = new QuoteOfTheMomentServer();
        node.start().join();
        System.out.println("QuoteOfTheMomentServer address is: " + node.identity().getPublicKey());
    }

    public static Quote getQuoteOfTheMoment() {
        return QUOTES[RandomUtil.randomInt(QUOTES.length - 1)];
    }

    public static final class Quote {
        private static final String ANSI_RESET = "\u001B[0m";
        private static final String ANSI_GREEN = "\u001B[32m";
        private static final String ANSI_BLUE = "\u001B[34m";
        public final String text;
        public final String from;

        // default constructor for jackson
        private Quote() {
            this.text = null;
            this.from = null;
        }

        private Quote(final String text, final String from) {
            this.text = text;
            this.from = from;
        }

        public static Quote of(final String quote, final String from) {
            return new Quote(quote, from);
        }

        @Override
        public String toString() {
            return ANSI_GREEN + "\"" + text + "\"" + ANSI_RESET + " — " + ANSI_BLUE + from + ANSI_RESET;
        }
    }
}
