package org.drasyl.jtasklet.broker.scheduler;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.util.Pair;

import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;

public class RandomSchedulingStrategy implements SchedulingStrategy {
    private static final Random RANDOM = new Random();

    @Override
    public String toString() {
        return "RandomSchedulingStrategy{}";
    }

    @Override
    public Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> vms) {
        final Map<DrasylAddress, ResourceProvider> availableVms = vms.entrySet().stream().filter(e -> e.getValue().state() == READY).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!availableVms.isEmpty()) {
            final DrasylAddress[] publicKeys = availableVms.keySet().toArray(new DrasylAddress[0]);
            final int rnd = RANDOM.nextInt(availableVms.size());
            final DrasylAddress publicKey = publicKeys[rnd];
            return Pair.of(publicKey, availableVms.get(publicKey));
        }
        else {
            return Pair.of(null, null);
        }
    }
}
