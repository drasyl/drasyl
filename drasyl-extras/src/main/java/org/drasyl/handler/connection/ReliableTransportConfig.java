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
package org.drasyl.handler.connection;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

@AutoValue
public abstract class ReliableTransportConfig {
    public static final ReliableTransportConfig DEFAULT = new AutoValue_ReliableTransportConfig.Builder()
            .issSupplier(Segment::randomSeq)
            .sndBufSupplier(SendBuffer::new)
            .rtnsQSupplier(channel -> new RetransmissionQueue())
            .rcfBufSupplier(ReceiveBuffer::new)
            .tcbSupplier((config, channel) -> new TransmissionControlBlock(
                    config,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    config.sndBufSupplier().apply(channel),
                    config.rtnsQSupplier().apply(channel),
                    config.rcfBufSupplier().apply(channel),
                    0,
                    0,
                    false
            ))
            .activeOpen(true)
            .baseMss(1432)
            .rmem(64 * 1432)
            // RFC 9293: Arbitrarily defined to be 2 minutes.
            .msl(ofMinutes(2))
            .noDelay(true) // FIXME: wieder deaktivieren
            .userTimeout(ofSeconds(60))
            .timestamps(true)
            // RFC 6298:       The above SHOULD be computed using alpha=1/8 and beta=1/4 (as
            // RFC 6298:       suggested in [JK88]).
            .alpha(1f / 8)
            .beta(1f / 4)
            // RFC 6298: where K = 4
            .k(4)
            .clock(new Clock() {
                @Override
                public long time() {
                    return System.nanoTime() / 1_000_000; // convert to ms
                }

                @Override
                public double g() {
                    return 1.0 / 1_000;
                }
            })
            // RFC 6298: (2.4) Whenever RTO is computed, if it is less than 1 second, then the RTO
            // RFC 6298:       SHOULD be rounded up to 1 second.
            .lBound(ofSeconds(1))
            // RFC 6298: (2.5) A maximum value MAY be placed on RTO provided it is at least 60
            // RFC 6298:       seconds.
            .uBound(ofSeconds(60))
            .sack(false)
            // RFC 9293: The override timeout should be in the range 0.1 - 1.0
            .overrideTimeout(ofMillis(100))
            .build();

    public static Builder newBuilder() {
        return DEFAULT.toBuilder();
    }

    public abstract LongSupplier issSupplier();

    public abstract Function<Channel, SendBuffer> sndBufSupplier();

    public abstract Function<Channel, RetransmissionQueue> rtnsQSupplier();

    public abstract Function<Channel, ReceiveBuffer> rcfBufSupplier();

    public abstract BiFunction<ReliableTransportConfig, Channel, TransmissionControlBlock> tcbSupplier();

    /**
     * @param activeOpen if {@code true} a handshake will be issued on {@link
     *                   #channelActive(ChannelHandlerContext)}. Otherwise, the remote peer must
     *                   initiate the handshake
     */
    public abstract boolean activeOpen();

    public abstract int baseMss();

    public abstract int rmem();

    /**
     * RFC 9293: Maximum Segment Lifetime, the time a TCP segment can exist in the internetwork RFC
     * 9293: system.
     */
    public abstract Duration msl();

    /**
     * bypass Nagle Delays by disabling Nagle's algorithm.
     */
    public abstract Duration userTimeout();

    public abstract boolean noDelay();

    /**
     * see timestamp option RFC 7323
     */
    public abstract boolean timestamps();

    /**
     * RFC 6298: smoothing factor
     */
    public abstract float alpha();

    /**
     * RFC 6298: delay variance factor
     */
    public abstract float beta();

    public abstract int k();

    public abstract Clock clock();

    public abstract Duration lBound();

    public abstract Duration uBound();

    public abstract boolean sack();

    public abstract Duration overrideTimeout();

    abstract Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder issSupplier(final LongSupplier issSupplier);

        public abstract Builder sndBufSupplier(final Function<Channel, SendBuffer> sndBufSupplier);

        public abstract Builder rtnsQSupplier(final Function<Channel, RetransmissionQueue> rtnsQSupplier);

        public abstract Builder rcfBufSupplier(final Function<Channel, ReceiveBuffer> rcfBufSupplier);

        public abstract Builder tcbSupplier(final BiFunction<ReliableTransportConfig, Channel, TransmissionControlBlock> tcbProvider);

        public abstract Builder activeOpen(final boolean activeOpen);

        public abstract Builder baseMss(final int baseMss);

        public abstract Builder rmem(final int rmem);

        public abstract Builder msl(final Duration msl);

        public abstract Builder noDelay(final boolean noDelay);

        public abstract Builder userTimeout(final Duration userTimeout);

        public abstract Builder timestamps(final boolean timestamps);

        public abstract Builder alpha(final float alpha);

        public abstract Builder beta(final float beta);

        public abstract Builder k(final int k);

        public abstract Builder clock(final Clock clock);

        /**
         * RFC 793: LBOUND is a lower bound on the timeout (e.g., 1 second)
         */
        public abstract Builder lBound(final Duration lBound);

        /**
         * RFC 793: UBOUND is an upper bound on the timeout (e.g., 1 minute)
         */
        public abstract Builder uBound(final Duration uBound);

        public abstract Builder sack(final boolean sack);

        public abstract Builder overrideTimeout(final Duration overrideTimeout);

        abstract ReliableTransportConfig autoBuild();

        public ReliableTransportConfig build() {
            return autoBuild();
        }
    }

    public interface Clock {
        long time();

        // clock granularity in seconds
        double g();
    }
}
