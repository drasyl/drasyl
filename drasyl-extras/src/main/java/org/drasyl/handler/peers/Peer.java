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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.util.EvictingQueue;

import java.net.InetSocketAddress;
import java.util.Queue;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.NumberUtil.sampleStandardDeviation;
import static org.drasyl.util.Preconditions.requireNonNegative;
import static org.drasyl.util.Preconditions.requirePositive;

public class Peer {
    public static final int RTTS_COUNT = 200;
    private final Role role;
    private final InetSocketAddress inetAddress;
    private final Long average;
    private final Double stDev;
    private final Queue<Long> records;
    private long sent;
    private long last;
    private long best;
    private long worst;

    @JsonCreator
    public Peer(@JsonProperty("role") final Role role,
                @JsonProperty("inetAddress") final InetSocketAddress inetAddress,
                @JsonProperty("sent") final long sent,
                @JsonProperty("last") final long last,
                @JsonProperty("average") final long average,
                @JsonProperty("best") final long best,
                @JsonProperty("worst") final long worst,
                @JsonProperty("stDev") final double stDev) {
        this.role = requireNonNull(role);
        this.inetAddress = requireNonNull(inetAddress);
        this.average = average;
        this.records = null;
        this.sent = requirePositive(sent);
        this.last = last;
        this.best = best;
        this.worst = worst;
        this.stDev = requireNonNegative(stDev);
    }

    public Peer(final Role role,
                final InetSocketAddress inetAddress,
                final long rtt) {
        this.role = requireNonNull(role);
        this.inetAddress = inetAddress;
        this.average = null;
        this.records = new EvictingQueue<>(RTTS_COUNT);
        records.add(rtt);
        this.sent = 1;
        this.last = rtt;
        this.best = rtt;
        this.worst = rtt;
        this.stDev = null;
    }

    public Peer(final Role role,
                final InetSocketAddress inetAddress) {
        this(role, inetAddress, -1);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "role=" + role() +
                ", inetAddress=" + inetAddress() +
                ", average=" + average() +
                ", stDev=" + stDev() +
                ", sent=" + sent +
                ", last=" + last() +
                ", best=" + best() +
                ", worst=" + worst() +
                '}';
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
    @JsonGetter
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
    @JsonGetter
    @SuppressWarnings({ "OptionalGetWithoutIsPresent", "ReplaceNullCheck" })
    public double average() {
        if (average != null) {
            return average;
        }
        else {
            return records.stream().mapToLong(l -> l).average().getAsDouble();
        }
    }

    /**
     * @return best RTT
     */
    @JsonGetter
    public long best() {
        return best;
    }

    /**
     * @return worst RTT
     */
    @JsonGetter
    public long worst() {
        return worst;
    }

    /**
     * @return RTT standard deviation
     */
    @JsonGetter
    @SuppressWarnings("ReplaceNullCheck")
    public double stDev() {
        if (stDev != null) {
            return stDev;
        }
        else {
            return sampleStandardDeviation(records.stream().mapToDouble(d -> d).toArray());
        }
    }
}
