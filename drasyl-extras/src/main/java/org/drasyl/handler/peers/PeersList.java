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
package org.drasyl.handler.peers;

import org.drasyl.identity.DrasylAddress;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.InetSocketAddressUtil.socketAddressToString;
import static org.drasyl.util.Preconditions.requirePositive;

public class PeersList {
    private final long time;
    private final Map<DrasylAddress, Peer> peers;

    PeersList(final long time, final Map<DrasylAddress, Peer> peers) {
        this.time = requirePositive(time);
        this.peers = requireNonNull(peers);
    }

    public PeersList(final Map<DrasylAddress, Peer> peers) {
        this(System.currentTimeMillis(), peers);
    }

    public Map<DrasylAddress, Peer> peers() {
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
        for (final Map.Entry<DrasylAddress, Peer> entry : peers.entrySet().stream().sorted(new PeerComparator()).collect(Collectors.toList())) {
            final DrasylAddress address = entry.getKey();
            final Peer peer = entry.getValue();

            // table row
            builder.append(String.format(
                    "%-64s  %-4s  %-45s  %4d  %4d  %,4.0f  %4d  %4d  %,5.1f%n",
                    address,
                    peer.role(),
                    peer.inetAddress() != null ? socketAddressToString(peer.inetAddress()) : "",
                    peer.sent(),
                    peer.last(),
                    peer.average(),
                    peer.best(),
                    peer.worst(),
                    peer.stDev()
            ));
        }

        return builder.toString();
    }
}
