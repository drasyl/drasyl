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
package org.drasyl.example.diningphilosophers;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.identity.CompressedPublicKey;

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
        return Behaviors.receive()
                .onMessage(Take.class, (sender, message) -> {
                    send(sender, new Taken(identity().getPublicKey()));
                    return takenBy(sender);
                })
                .build();
    }

    private Behavior takenBy(final CompressedPublicKey philosopher) {
        return Behaviors.receive()
                .onMessage(Take.class, (sender, message) -> {
                    send(sender, new Busy(identity().getPublicKey()));
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
        final CompressedPublicKey fork;

        Answer(final CompressedPublicKey fork) {
            this.fork = requireNonNull(fork);
        }

        abstract boolean isTaken();

        abstract boolean isBusy();
    }

    static class Taken extends Answer {
        public Taken(final CompressedPublicKey fork) {
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
        public Busy(final CompressedPublicKey fork) {
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
