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
package org.drasyl.example.diningphilosophers;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.identity.IdentityPublicKey;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("java:S2094")
public class Fork extends BehavioralDrasylNode {
    final String name;

    protected Fork(final String name) throws DrasylException {
        super(DrasylConfig.newBuilder()
                .identityPath(Path.of(name + ".identity.json"))
                .build());
        this.name = requireNonNull(name);
    }

    @Override
    protected Behavior created() {
        return available();
    }

    private Behavior available() {
        return newBehaviorBuilder()
                .onMessage(Take.class, (sender, message) -> {
                    send(sender, new Taken(identity().getIdentityPublicKey()));
                    return takenBy(sender);
                })
                .build();
    }

    private Behavior takenBy(final IdentityPublicKey philosopher) {
        return newBehaviorBuilder()
                .onMessage(Take.class, (sender, message) -> {
                    send(sender, new Busy(identity().getIdentityPublicKey()));
                    return Behaviors.same();
                })
                .onMessage(Put.class, (sender, message) -> sender.equals(philosopher), (sender, message) -> available())
                .build();
    }

    static class Take {
    }

    static class Put {
    }

    @SuppressWarnings("java:S118")
    abstract static class Answer {
        final IdentityPublicKey fork;

        Answer(final IdentityPublicKey fork) {
            this.fork = requireNonNull(fork);
        }

        abstract boolean isTaken();

        abstract boolean isBusy();
    }

    static class Taken extends Answer {
        public Taken(final IdentityPublicKey fork) {
            super(fork);
        }

        @Override
        boolean isTaken() {
            return true;
        }

        @Override
        boolean isBusy() {
            return false;
        }
    }

    static class Busy extends Answer {
        public Busy(final IdentityPublicKey fork) {
            super(fork);
        }

        @Override
        boolean isTaken() {
            return false;
        }

        @Override
        boolean isBusy() {
            return true;
        }
    }
}
