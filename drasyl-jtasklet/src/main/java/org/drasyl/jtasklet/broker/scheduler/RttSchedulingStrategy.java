package org.drasyl.jtasklet.broker.scheduler;

import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;

public class RttSchedulingStrategy implements SchedulingStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(RttSchedulingStrategy.class);
    private final SchedulingStrategy FALLBACK = new RandomSchedulingStrategy();

    @Override
    public String toString() {
        return "RttSchedulingStrategy{}";
    }

    @Override
    public Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> providers,
                                                          final Map<DrasylAddress, PeersRttReport> rttReports,
                                                          final DrasylAddress consumer,
                                                          final List<String> tags,
                                                          final int priority) {
        final Map<Pair<DrasylAddress, DrasylAddress>, Double> rtts = new HashMap<>();
        for (final Entry<DrasylAddress, PeersRttReport> entry : rttReports.entrySet()) {
            final DrasylAddress source = entry.getKey();
            for (final Entry<DrasylAddress, PeerRtt> entry2 : entry.getValue().peers().entrySet()) {
                final DrasylAddress destination = entry2.getKey();
                final PeerRtt peerRtt = entry2.getValue();
                rtts.put(Pair.of(source, destination), peerRtt.average());
            }
        }

        LOG.info("My RTT table: {}.", rtts);

        final Map<DrasylAddress, ResourceProvider> availableVms = providers.entrySet().stream().filter(e -> e.getValue().state() == READY).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        if (!availableVms.isEmpty()) {
            double minRtt = Double.MAX_VALUE;
            DrasylAddress bestAddress = null;
            ResourceProvider bestProvider = null;
            for (final Entry<DrasylAddress, ResourceProvider> entry : availableVms.entrySet()) {
                final DrasylAddress address = entry.getKey();
                final ResourceProvider provider = entry.getValue();

                final Double consumerToProviderRtt = rtts.get(Pair.of(consumer, address));
                final Double providerToConsumerRtt = rtts.get(Pair.of(address, consumer));
                if (consumerToProviderRtt != null && providerToConsumerRtt != null) {
                    final double rtt = Math.max(consumerToProviderRtt, providerToConsumerRtt);
                    if (rtt < minRtt) {
                        minRtt = rtt;
                        bestAddress = address;
                        bestProvider = provider;
                    }
                }
            }

            if (bestAddress == null) {
                LOG.info("No RTT information between any available Providers and Consumer {} available. Fall back to {} scheduling", consumer, FALLBACK);
                return FALLBACK.schedule(providers, rttReports, consumer, tags, priority);
            }
            else {
                LOG.info("Schedule request from Consumer {} to Provider {}. The have a RTT of {}ms", consumer, bestProvider, minRtt);
                return Pair.of(bestAddress, bestProvider);
            }
        }
        else {
            return Pair.of(null, null);
        }
    }
}
