package org.drasyl.jtasklet.broker.scheduler;

import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.util.Pair;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;

public class BenchmarkSchedulingStrategy implements SchedulingStrategy {
    @Override
    public String toString() {
        return "BenchmarkSchedulingStrategy{}";
    }

    @Override
    public Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> providers,
                                                          final Map<DrasylAddress, PeersRttReport> rttReports,
                                                          final DrasylAddress consumer) {
        final Map<DrasylAddress, ResourceProvider> availableVms = providers.entrySet().stream().filter(e -> e.getValue().state() == READY).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        if (!availableVms.isEmpty()) {
            long minBenchmark = Long.MAX_VALUE;
            DrasylAddress bestAddress = null;
            ResourceProvider bestProvider = null;
            for (final Entry<DrasylAddress, ResourceProvider> entry : availableVms.entrySet()) {
                final DrasylAddress address = entry.getKey();
                final ResourceProvider provider = entry.getValue();

                if (provider.benchmark() < minBenchmark) {
                    minBenchmark = provider.benchmark();
                    bestAddress = address;
                    bestProvider = provider;
                }
            }

            return Pair.of(bestAddress, bestProvider);
        }
        else {
            return Pair.of(null, null);
        }
    }
}
