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
import org.drasyl.event.Event;
import org.drasyl.example.diningphilosophers.Fork.Take;
import org.drasyl.identity.CompressedPublicKey;

import java.nio.file.Path;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

@SuppressWarnings({ "java:S106", "java:S109" })
public class Philosopher extends BehavioralDrasylNode {
    private final String name;
    private final String leftName;
    private final CompressedPublicKey leftAddress;
    private final String rightName;
    private final CompressedPublicKey rightAddress;

    public Philosopher(final String name,
                       final String leftName,
                       final CompressedPublicKey leftAddress,
                       final String rightName,
                       final CompressedPublicKey rightAddress) throws DrasylException {
        super(DrasylConfig.newBuilder()
                .identityPath(Path.of(name + ".identity.json"))
                .build());
        this.name = requireNonNull(name);
        this.leftName = requireNonNull(leftName);
        this.leftAddress = requireNonNull(leftAddress);
        this.rightName = requireNonNull(rightName);
        this.rightAddress = requireNonNull(rightAddress);
    }

    @Override
    protected Behavior created() {
        return waiting();
    }

    private Behavior waiting() {
        return Behaviors.receive()
                .onEvent(Think.class, event -> {
                    System.out.println(name + " starts to think");
                    return startThinking(Duration.ofSeconds(5));
                })
                .build();
    }

    private Behavior thinking() {
        return Behaviors.receive()
                .onEvent(Eat.class, event -> {
                    send(leftAddress, new Take());
                    send(rightAddress, new Take());
                    return hungry();
                })
                .build();
    }

    private Behavior hungry() {
        return Behaviors.receive()
                .onMessage(Fork.Answer.class, (sender, message) -> message.isBusy(), (sender, message) -> firstForkDenied())
                .onMessage(Fork.Answer.class, (sender, message) -> sender.equals(leftAddress), (sender, message) -> waitForOtherFork(rightAddress, leftAddress))
                .onMessage(Fork.Answer.class, (sender, message) -> sender.equals(rightAddress), (sender, message) -> waitForOtherFork(leftAddress, rightAddress))
                .build();
    }

    private Behavior eating() {
        return Behaviors.receive()
                .onEvent(Think.class, event -> {
                    System.out.println(name + " puts down his forks and starts to think");
                    send(leftAddress, new Fork.Put());
                    send(rightAddress, new Fork.Put());
                    return startThinking(Duration.ofSeconds(5));
                }).build();
    }

    private Behavior startThinking(final Duration duration) {
        return Behaviors.withScheduler(scheduler -> {
            scheduler.scheduleEvent(new Eat(), duration);
            return thinking();
        });
    }

    private Behavior firstForkDenied() {
        return Behaviors.receive()
                .onMessage(Fork.Answer.class, (sender, message) -> message.isTaken(), (sender, message) -> {
                    send(sender, new Fork.Put());
                    return startThinking(Duration.ofMillis(10));
                })
                .onMessage(Fork.Answer.class, (sender, message) -> message.isBusy(), (sender, message) ->
                        startThinking(Duration.ofMillis(10))
                )
                .build();
    }

    private Behavior waitForOtherFork(final CompressedPublicKey forkToWaitFor,
                                      final CompressedPublicKey takenFork) {
        return Behaviors.receive()
                .onMessage(Fork.Answer.class, (sender, message) -> message.isTaken() && sender.equals(forkToWaitFor), (sender, message) -> {
                    System.out.println(name + " has picked up " + leftName + " and " + rightName + " and starts to eat");
                    return startEating(Duration.ofSeconds(5));
                })
                .onMessage(Fork.Answer.class, (sender, message) -> message.isBusy() && sender.equals(forkToWaitFor), (sender, message) -> {
                    send(takenFork, new Fork.Put());
                    return startThinking(Duration.ofMillis(10));
                })
                .build();
    }

    private Behavior startEating(final Duration duration) {
        return Behaviors.withScheduler(scheduler -> {
            scheduler.scheduleEvent(new Think(), duration);
            return eating();
        });
    }

    static class Think implements Event {
    }

    static class Eat implements Event {
    }
}
