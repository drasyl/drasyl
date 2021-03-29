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

import org.drasyl.DrasylException;
import org.drasyl.example.diningphilosophers.Philosopher.Think;

import java.util.ArrayList;
import java.util.List;

/**
 * This example demonstrates how drasyl nodes can be implemented as finite state machines using the
 * <a href="https://en.wikipedia.org/wiki/Dining_philosophers_problem">dining philosophers
 * problem</a>.
 * <p>
 * Adapted from: <a href="https://developer.lightbend.com/start/?group=akka&project=akka-samples-fsm-java">Akka</a>
 */
@SuppressWarnings({ "java:S106", "java:S2096" })
public class DiningPhilosophers {
    private static final String[] PHILOSOPHERS = System.getProperty("philosophers", "Fiona,Lip,Ian,Carl,Debbie").split(",");

    public static void main(final String[] args) throws DrasylException {
        // create forks
        final List<Fork> forks = new ArrayList<>();
        for (int i = 1; i <= PHILOSOPHERS.length; i++) {
            final String name = "Fork#" + i;
            System.out.println("Create " + name);
            final Fork fork = new Fork(name);
            fork.start();
            forks.add(fork);
        }

        // create philosophers and assign them their left and right fork
        final List<Philosopher> philosophers = new ArrayList<>();
        for (int i = 0; i < PHILOSOPHERS.length; i++) {
            final String name = PHILOSOPHERS[i];
            System.out.println("Create philosopher " + name);
            final Fork leftFork = forks.get(i);
            final Fork rightFork = forks.get((i + 1) % PHILOSOPHERS.length);
            final Philosopher philosopher = new Philosopher(name, leftFork.name, leftFork.identity().getPublicKey(), rightFork.name, rightFork.identity().getPublicKey());
            philosopher.start();
            philosophers.add(philosopher);
        }

        // signal all philosophers that they should stark thinking, and watch the show
        philosophers.forEach(philosopher -> philosopher.onEvent(new Think()));
    }
}
