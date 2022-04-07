/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.EvictingQueue;
import org.drasyl.util.NumberUtil;

import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class PeersRttHandler extends ChannelInboundHandlerAdapter {
    private final Map<DrasylAddress, PeerRtt> rtts = new HashMap<>();

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.executor().scheduleWithFixedDelay(() -> {
            System.err.printf("%-64s  %-4s  %-45s  %-4s  %-4s  %-4s  %-4s  %-4s  %-4s%n", "Address", "Role", "Inet Address", " Snt", "Last", " Avg", "Best", "Wrst", "StDev");
            for (final Map.Entry<DrasylAddress, PeerRtt> entry : rtts.entrySet().stream().sorted(new Comparator<Map.Entry<DrasylAddress, PeerRtt>>() {
                @Override
                public int compare(final Map.Entry<DrasylAddress, PeerRtt> o1,
                                   final Map.Entry<DrasylAddress, PeerRtt> o2) {
                    return o1.getKey().toString().compareTo(o2.getKey().toString());
                }
            }).collect(Collectors.toList())) {
                final DrasylAddress address = entry.getKey();
                final PeerRtt peerRtt = entry.getValue();

                System.err.printf(
                        "%-64s  %-4s  %-45s   %4d  %4d  %,4.0f  %4d  %4d  %,4.0f%n",
                        address,
                        peerRtt.role(),
                        peerRtt.inetAddress().getHostString() + ":" + peerRtt.inetAddress().getPort(),
                        peerRtt.sent(),
                        peerRtt.last(),
                        peerRtt.average(),
                        peerRtt.best(),
                        peerRtt.worst(),
                        peerRtt.stDev()
                );
            }
            System.err.println();
        }, 0L, 5_000L, MILLISECONDS);

        ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof AddPathAndSuperPeerEvent) {
            final DrasylAddress address = ((AddPathAndSuperPeerEvent) evt).getAddress();
            final InetSocketAddress inetAddress = ((AddPathAndSuperPeerEvent) evt).getInetAddress();
            final long rtt = ((AddPathAndSuperPeerEvent) evt).getRtt();

            final PeerRtt peerRtt = new PeerRtt(PeerRtt.Role.SUPER, inetAddress, rtt);
            rtts.put(address, peerRtt);
        }
        else if (evt instanceof AddPathEvent) {
            final DrasylAddress address = ((AddPathEvent) evt).getAddress();
            final InetSocketAddress inetAddress = ((AddPathEvent) evt).getInetAddress();
            final long rtt = ((AddPathEvent) evt).getRtt();

            final PeerRtt peerRtt = new PeerRtt(PeerRtt.Role.DEFAULT, inetAddress, rtt);
            rtts.put(address, peerRtt);
        }
        else if (evt instanceof PathRttEvent) {
            final DrasylAddress address = ((PathRttEvent) evt).getAddress();
            final long rtt = ((PathRttEvent) evt).getRtt();

            final PeerRtt peerRtt = rtts.get(address);
            if (peerRtt != null) {
                peerRtt.last(rtt);
            }
        }
        else if (evt instanceof RemoveSuperPeerAndPathEvent || evt instanceof RemovePathEvent) {
            rtts.remove(((PathEvent) evt).getAddress());
        }

        ctx.fireUserEventTriggered(evt);
    }

    static class PeerRtt {
        private enum Role {
            SUPER("S"),
            CHILDREN("C"),
            DEFAULT("");
            private final String label;

            Role(final String label) {
                this.label = requireNonNull(label);
            }

            @Override
            public String toString() {
                return label;
            }
        }

        private final Role role;
        private final InetSocketAddress inetAddress;
        private final Queue<Long> pings;
        private long sent;
        private long last;
        private long best;
        private long worst;

        PeerRtt(final Role role,
                final InetSocketAddress inetAddress,
                final long rtt) {
            this.role = requireNonNull(role);
            this.inetAddress = requireNonNull(inetAddress);
            this.pings = new EvictingQueue<>(200);
            pings.add(rtt);
            this.sent = 1;
            this.last = rtt;
            this.best = rtt;
            this.worst = rtt;
        }

        public Role role() {
            return role;
        }

        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        public long sent() {
            return sent;
        }

        public long last() {
            return last;
        }

        void last(final long rtt) {
            pings.add(rtt);
            sent++;
            last = rtt;
            if (last < best) {
                best = last;
            }
            else if (last > worst) {
                worst = rtt;
            }
        }

        public double average() {
            return pings.stream().mapToLong(l -> l).average().getAsDouble();
        }

        public long best() {
            return best;
        }

        public long worst() {
            return worst;
        }

        public double stDev() {
            return NumberUtil.sampleStandardDeviation(pings.stream().mapToDouble(d -> d).toArray());
        }
    }
}
