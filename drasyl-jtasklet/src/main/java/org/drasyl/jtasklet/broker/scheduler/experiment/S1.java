/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.jtasklet.broker.scheduler.experiment;

import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.jtasklet.broker.scheduler.SchedulingStrategy;
import org.drasyl.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;

/**
 * Real-Time Tasks: Random
 * Low-Prio Tasks: Random
 */
public class S1 implements SchedulingStrategy {
    private static final Random RANDOM = new Random(555);

    @Override
    public String toString() {
        return "S1{}";
    }

    @Override
    public Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> providers,
                                                          final Map<DrasylAddress, PeersRttHandler.PeersRttReport> rttReports,
                                                          final DrasylAddress consumer,
                                                          final List<String> tags,
                                                          final int priority) {
        final Map<DrasylAddress, ResourceProvider> availableVms = providers.entrySet().stream().filter(e -> e.getValue().state() == READY).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!availableVms.isEmpty()) {
            final DrasylAddress[] publicKeys = availableVms.keySet().toArray(new DrasylAddress[0]);

            final int rnd = RANDOM.nextInt(availableVms.size() - 1);
            final DrasylAddress publicKey = publicKeys[rnd];
            return Pair.of(publicKey, availableVms.get(publicKey));
        }
        else {
            return Pair.of(null, null);
        }
    }
}
