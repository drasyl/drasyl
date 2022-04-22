package org.drasyl.jtasklet.broker.scheduler;

import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.drasyl.jtasklet.broker.ResourceProvider.ProviderState.READY;

public class RttSchedulingStrategy implements SchedulingStrategy {
    @Override
    public Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> providers,
                                                          final Map<DrasylAddress, PeersRttReport> rttReports,
                                                          final DrasylAddress consumer) {
        // build matrix
//        final Set<DrasylAddress> addresses = new HashSet<>();
//        addresses.addAll(providers.keySet());
//        addresses.addAll(rttReports.keySet());
//        rttReports.values().stream().map(PeersRttReport::peers).map(Map::keySet).forEach(addresses::addAll);

        final Map<DrasylAddress, Map<DrasylAddress, Integer>> matrix = new HashMap<>();
        for (final Entry<DrasylAddress, Double> entry : rttReports.entrySet()) {
            final DrasylAddress source = entry.getKey();
            final Map<DrasylAddress, Integer> map = new HashMap<>();
            for (final Entry<DrasylAddress, PeerRtt> entry2 : entry.getValue().peers().entrySet()) {
                final DrasylAddress destination = entry2.getKey();
                final PeerRtt peerRtt = entry2.getValue();
                map.put(destination, peerRtt.average());
            }
        }

        final Map<DrasylAddress, ResourceProvider> availableVms = providers.entrySet().stream().filter(e -> e.getValue().state() == READY).collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        return Pair.of(null, null);
    }
}
