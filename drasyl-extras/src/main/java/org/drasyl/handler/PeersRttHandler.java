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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.PathRttEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.EvictingQueue;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.NumberUtil.sampleStandardDeviation;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * A {@link io.netty.channel.ChannelHandler} that tracks all {@link PathEvent}s containing RTT
 * information and generates some statistics that are periodically passed to the channel as an
 * {@link PeersRttReport} event.
 */
@UnstableApi
public class PeersRttHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(PeersRttHandler.class);
    private final long emitEventInterval;
    private final Map<DrasylAddress, PeerRtt> rtts;
    private ScheduledFuture<?> scheduledFuture;

    PeersRttHandler(final long emitEventInterval,
                    final Map<DrasylAddress, PeerRtt> rtts) {
        this.emitEventInterval = requirePositive(emitEventInterval);
        this.rtts = requireNonNull(rtts);
    }

    /**
     * @param emitEventInterval time in ms how often report should be generated
     */
    public PeersRttHandler(final long emitEventInterval) {
        this(emitEventInterval, new HashMap<>());
    }

    public PeersRttHandler() {
        this(5_000L); // NOSONAR
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            scheduleTask(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        scheduleTask(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        ctx.fireChannelInactive();
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
            LOG.debug("Got AddPathAndSuperPeerEvent from `{}`: {}", address, peerRtt);
        }
        else if (evt instanceof AddPathEvent) {
            final DrasylAddress address = ((AddPathEvent) evt).getAddress();
            final InetSocketAddress inetAddress = ((AddPathEvent) evt).getInetAddress();
            final long rtt = ((AddPathEvent) evt).getRtt();

            final PeerRtt peerRtt = new PeerRtt(PeerRtt.Role.DEFAULT, inetAddress, rtt);
            rtts.put(address, peerRtt);

            LOG.debug("Got AddPathEvent from `{}`: {}", address, peerRtt);
        }
        else if (evt instanceof PathRttEvent) {
            final DrasylAddress address = ((PathRttEvent) evt).getAddress();
            final long rtt = ((PathRttEvent) evt).getRtt();

            final PeerRtt peerRtt = rtts.get(address);
            if (peerRtt != null) {
                peerRtt.last(rtt);
            }

            LOG.debug("Got PathRttEvent from `{}`: {}", address, peerRtt);
        }
        else if (evt instanceof RemoveSuperPeerAndPathEvent || evt instanceof RemovePathEvent) {
            rtts.remove(((PathEvent) evt).getAddress());

            LOG.debug("Got RemovePathEvent for {}", ((PathEvent) evt).getAddress());
        }

        ctx.fireUserEventTriggered(evt);
    }

    private void scheduleTask(final ChannelHandlerContext ctx) {
        scheduledFuture = ctx.executor().scheduleWithFixedDelay(() -> {
            final PeersRttReport report = new PeersRttReport(rtts);
            ctx.fireUserEventTriggered(report);
        }, 0L, emitEventInterval, MILLISECONDS);
    }

    public static class PeersRttReport {
        private final long time;
        private final Map<DrasylAddress, PeerRtt> peers;

        @JsonCreator
        public PeersRttReport(@JsonProperty("time") final long time,
                              @JsonProperty("peers") final Map<DrasylAddress, PeerRtt> peers) {
            this.time = requireNonNegative(time);
            this.peers = requireNonNull(peers);
        }

        public PeersRttReport(final Map<DrasylAddress, PeerRtt> peers) {
            this(System.currentTimeMillis(), peers);
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
    }

    public static class PeerRtt {
        public static final int RTTS_COUNT = 200;
        private final Role role;
        private final InetSocketAddress inetAddress;
        private final Queue<Long> records;
        private long sent;
        private long last;
        private long best;
        private long worst;

        @JsonCreator
        public PeerRtt(@JsonProperty("role") final Role role,
                       @JsonProperty("inetAddress") final InetSocketAddress inetAddress,
                       @JsonProperty("last") final long rtt) {
            this.role = requireNonNull(role);
            this.inetAddress = requireNonNull(inetAddress);
            this.records = new EvictingQueue<>(RTTS_COUNT);
            records.add(rtt);
            this.sent = 1;
            this.last = rtt;
            this.best = rtt;
            this.worst = rtt;
        }

        @JsonGetter
        public Role role() {
            return role;
        }

        @JsonGetter
        public InetSocketAddress inetAddress() {
            return inetAddress;
        }

        /**
         * @return number of pings sent
         */
        @JsonIgnore
        public long sent() {
            return sent;
        }

        /**
         * @return RTT of last ping
         */
        @JsonGetter
        public long last() {
            return last;
        }

        void last(final long rtt) {
            records.add(rtt);
            sent++;
            last = rtt;
            if (last < best) {
                best = last;
            }
            else if (last > worst) {
                worst = rtt;
            }
        }

        /**
         * @return average RTT
         */
        @JsonIgnore
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        public double average() {
            return records.stream().mapToLong(l -> l).average().getAsDouble();
        }

        /**
         * @return best RTT
         */
        @JsonIgnore
        public long best() {
            return best;
        }

        /**
         * @return worst RTT
         */
        @JsonIgnore
        public long worst() {
            return worst;
        }

        /**
         * @return RTT standard deviation
         */
        @JsonIgnore
        public double stDev() {
            return sampleStandardDeviation(records.stream().mapToDouble(d -> d).toArray());
        }

        @Override
        public String toString() {
            return "PeerRtt{" +
                    "role=" + role +
                    ", inetAddress=" + inetAddress +
                    ", sent=" + sent +
                    ", last=" + last +
                    ", best=" + best +
                    ", worst=" + worst +
                    '}';
        }

        public enum Role {
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
    }

    private static class EntryComparator implements Comparator<Entry<DrasylAddress, PeerRtt>> {
        @Override
        public int compare(final Entry<DrasylAddress, PeerRtt> o1,
                           final Entry<DrasylAddress, PeerRtt> o2) {
            return o1.getKey().toString().compareTo(o2.getKey().toString());
        }
    }
}
