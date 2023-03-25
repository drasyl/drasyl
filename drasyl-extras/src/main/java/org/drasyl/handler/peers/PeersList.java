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

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.InetSocketAddressUtil.socketAddressToString;

public class PeersList {
    private final Map<DrasylAddress, Peer> peers;

    public PeersList(final Map<DrasylAddress, Peer> peers) {
        this.peers = requireNonNull(peers);
    }

    public Map<DrasylAddress, Peer> peers() {
        return peers;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        // table header
        builder.append(String.format("%-64s  %4s  %-45s  %4s  %4s  %4s  %4s  %4s  %5s%n", "Peer", "Role", "Inet Address", "Snt", "Last", " Avg", "Best", "Wrst", "StDev"));

        // table body
        for (final Entry<DrasylAddress, Peer> entry : peers.entrySet().stream().sorted(new PeerComparator()).collect(Collectors.toList())) {
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
