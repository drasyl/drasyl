package org.drasyl.jtasklet.broker.scheduler;

import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;
import static org.drasyl.util.RandomUtil.randomInt;

public class RandomSchedulingStrategy implements SchedulingStrategy {
    @Override
    public String toString() {
        return "RandomSchedulingStrategy{}";
    }

    @Override
    public Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> providers,
                                                          final Map<DrasylAddress, PeersRttReport> rttReports,
                                                          final DrasylAddress consumer,
                                                          final List<String> tags,
                                                          final int priority) {
        final Map<DrasylAddress, ResourceProvider> availableVms = providers.entrySet().stream().filter(e -> e.getValue().state() == READY).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!availableVms.isEmpty()) {
            final DrasylAddress[] publicKeys = availableVms.keySet().toArray(new DrasylAddress[0]);
            final int rnd = randomInt(availableVms.size() - 1);
            final DrasylAddress publicKey = publicKeys[rnd];
            return Pair.of(publicKey, availableVms.get(publicKey));
        }
        else {
            return Pair.of(null, null);
        }
    }
}