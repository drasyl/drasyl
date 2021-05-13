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
import org.drasyl.event.Event;
import org.drasyl.example.diningphilosophers.Fork.Take;
import org.drasyl.identity.IdentityPublicKey;

import java.nio.file.Path;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

@SuppressWarnings({ "java:S106", "java:S109" })
public class Philosopher extends BehavioralDrasylNode {
    private final String name;
    private final String leftName;
    private final IdentityPublicKey leftAddress;
    private final String rightName;
    private final IdentityPublicKey rightAddress;

    public Philosopher(final String name,
                       final String leftName,
                       final IdentityPublicKey leftAddress,
                       final String rightName,
                       final IdentityPublicKey rightAddress) throws DrasylException {
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

    private Behavior waitForOtherFork(final IdentityPublicKey forkToWaitFor,
                                      final IdentityPublicKey takenFork) {
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
