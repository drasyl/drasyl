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
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.EvictingQueue;
import org.drasyl.util.NumberUtil;

import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * A {@link io.netty.channel.ChannelHandler} that tracks all {@link PathEvent}s containing RTT
 * information and generates some statistics that are periodically passed to the channel as an
 * {@link PeersRttReport} event and optionally written to {@link System#out}.
 */
public class PeersRttHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream printStream;
    private final long printInterval;
    private final Map<DrasylAddress, PeerRtt> rtts;
    private ScheduledFuture<?> scheduledFuture;

    PeersRttHandler(final PrintStream printStream,
                    final long printInterval,
                    final Map<DrasylAddress, PeerRtt> rtts) {
        this.printStream = printStream;
        this.printInterval = requirePositive(printInterval);
        this.rtts = requireNonNull(rtts);
    }

    /**
     * @param printStream    if not {@code null}, the RTT statistics will be written to this {@link
     *                       PrintStream}
     * @param reportInterval time in ms how often report should be generated
     */
    public PeersRttHandler(final PrintStream printStream, final long reportInterval) {
        this(printStream, reportInterval, new HashMap<>());
    }

    public PeersRttHandler() {
        this(System.err, 5_000L); // NOSONAR
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
    public void channelInactive(ChannelHandlerContext ctx) {
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

    private void scheduleTask(final ChannelHandlerContext ctx) {
        scheduledFuture = ctx.executor().scheduleWithFixedDelay(() -> {
            final PeersRttReport report = new PeersRttReport(rtts);
            ctx.fireUserEventTriggered(report);
            if (printStream != null) {
                printStream.println(report);
            }
        }, 0L, printInterval, MILLISECONDS);
    }

    public static class PeerRtt {
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

        private final Role role;
        private final InetSocketAddress inetAddress;
        private Queue<Long> pings;
        private long sent;
        private long last;
        private double average;
        private long best;
        private long worst;
        private double stDev;

        @JsonCreator
        public PeerRtt(@JsonProperty("role") final Role role,
                       @JsonProperty("inetAddress") final InetSocketAddress inetAddress,
                       @JsonProperty("sent") final long sent,
                       @JsonProperty("last") final long last,
                       @JsonProperty("average") final double average,
                       @JsonProperty("best") final long best,
                       @JsonProperty("worst") final long worst,
                       @JsonProperty("stDev") final double stDev) {
            this.role = requireNonNull(role);
            this.inetAddress = requireNonNull(inetAddress);
            this.sent = requirePositive(sent);
            this.last = last;
            this.average = average;
            this.best = best;
            this.worst = worst;
            this.stDev = stDev;
        }

        public PeerRtt(final Role role,
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

        /**
         * @return number of pings sent
         */
        public long sent() {
            return sent;
        }

        /**
         * @return RTT of last ping
         */
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

        /**
         * @return average RTT
         */
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        public double average() {
            if (pings != null) {
                return pings.stream().mapToLong(l -> l).average().getAsDouble();
            }
            else {
                return average;
            }
        }

        /**
         * @return best RTT
         */
        public long best() {
            return best;
        }

        /**
         * @return worst RTT
         */
        public long worst() {
            return worst;
        }

        /**
         * @return worst RTT
         */
        public double stDev() {
            if (pings != null) {
                return NumberUtil.sampleStandardDeviation(pings.stream().mapToDouble(d -> d).toArray());
            }
            else {
                return stDev;
            }
        }
    }
}
