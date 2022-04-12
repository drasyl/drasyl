package org.drasyl.handler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.identity.DrasylAddress;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.Preconditions.requirePositive;

public class PeersRttReport {
    private final long time;
    private final Map<DrasylAddress, PeerRtt> peers;

    @JsonCreator
    public PeersRttReport(@JsonProperty("time") final long time,
                          @JsonProperty("peers") final Map<DrasylAddress, PeerRtt> peers) {
        this.time = requirePositive(time);
        this.peers = requireNonNull(peers);
    }

    public PeersRttReport(final Map<DrasylAddress, PeerRtt> peers) {
        this(System.currentTimeMillis(), peers);
    }

    public long time() {
        return time;
    }

    public Map<DrasylAddress, PeerRtt> peers() {
        return peers;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        // table header
        final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), Clock.systemDefaultZone().getZone());
        builder.append(String.format("Time: %-35s%98s%n", RFC_1123_DATE_TIME.format(zonedDateTime), "RTTs"));
        builder.append(String.format("%-64s  %4s  %-45s  %4s  %4s  %4s  %4s  %4s  %5s%n", "Peer", "Role", "Inet Address", "Snt", "Last", " Avg", "Best", "Wrst", "StDev"));

        // table body
        for (final Entry<DrasylAddress, PeerRtt> entry : peers.entrySet().stream().sorted(new EntryComparator()).collect(Collectors.toList())) {
            final DrasylAddress address = entry.getKey();
            final PeerRtt peerRtt = entry.getValue();

            // table row
            builder.append(String.format(
                    "%-64s  %-4s  %-45s  %4d  %4d  %,4.0f  %4d  %4d  %,5.1f%n",
                    address,
                    peerRtt.role(),
                    peerRtt.inetAddress().getHostString() + ":" + peerRtt.inetAddress().getPort(),
                    peerRtt.sent(),
                    peerRtt.last(),
                    peerRtt.average(),
                    peerRtt.best(),
                    peerRtt.worst(),
                    peerRtt.stDev()
            ));
        }

        return builder.toString();
    }

    private static class EntryComparator implements Comparator<Entry<DrasylAddress, PeerRtt>> {
        @Override
        public int compare(final Entry<DrasylAddress, PeerRtt> o1,
                           final Entry<DrasylAddress, PeerRtt> o2) {
            return o1.getKey().toString().compareTo(o2.getKey().toString());
        }
    }
}
