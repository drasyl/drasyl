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
