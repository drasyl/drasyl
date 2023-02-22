package org.drasyl.jtasklet.broker.scheduler;

import org.drasyl.handler.PeersRttHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;
import org.drasyl.util.Pair;

import java.util.List;
import java.util.Map;

public interface SchedulingStrategy {
    Pair<DrasylAddress, ResourceProvider> schedule(final Map<DrasylAddress, ResourceProvider> providers,
                                                   final Map<DrasylAddress, PeersRttHandler.PeersRttReport> rttReports,
                                                   final DrasylAddress consumer,
                                                   final List<String> tags,
                                                   final int priority);
}
