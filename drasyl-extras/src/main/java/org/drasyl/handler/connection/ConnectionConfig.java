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
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.drasyl.handler.connection.TransmissionControlBlock.MAX_PORT;
import static org.drasyl.handler.connection.TransmissionControlBlock.MIN_PORT;
import static org.drasyl.util.RandomUtil.randomInt;

@AutoValue
public abstract class ConnectionConfig {
    // Google Cloud applied MTU is 1460
    public static final int IP_MTU = 1460;
    // here, drasyl replaces IP
    // IPv4: 20 bytes
    // UDP: 8 bytes
    // drasyl: 120 bytes
    public static final int DRASYL_HDR_SIZE = 20 + 8 + 4 + 116;
    static final ConnectionConfig DEFAULT = new AutoValue_ConnectionConfig.Builder()
            .unusedPortSupplier(() -> randomInt(MIN_PORT, MAX_PORT))
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
                    0,
                    0,
                    config.sndBufSupplier().apply(channel),
                    config.rtnsQSupplier().apply(channel),
                    config.rcfBufSupplier().get(),
                    0,
                    0,
                    false
            ))
            .activeOpen(true)
            .rmem(65_535 * 10)
            // RFC 9293 suggests a 2-minute MSL, leading to a 4-minute wait for "late" segments to
            // prevent accidentally using a recently released port associated with a previous
            // connection. However, our implementation restricts to one connection per peer. A
            // 4-minute MSL would delay channel close completion by 4 minutes and also prevent any
            // new connection for this time period.
            // Thus, we set MSL to 2 seconds for efficiency.
            .msl(ofSeconds(2))
            .noDelay(true)
            .overrideTimeout(ofMillis(100))
            .fs(1d / 2)
            .userTimeout(ofSeconds(60))
            .timestamps(false)
            .rto(ofSeconds(1))
            .lBound(ofSeconds(1))
            .uBound(ofSeconds(60))
            .alpha(1d / 8)
            .beta(1d / 4)
            .k(4)
            .clock(new Clock() {
                private long offset;

                @Override
                public long time() {
                    final long time = System.nanoTime() / 100_000; // convert to 10ms granularity
                    if (offset == 0) {
                        // clocks does not require to be synchronized between sender and receiver.
                        // to reduce time till  overflow, start at 0
                        offset = time;
                    }
                    return time - offset;
                }

                @Override
                public double g() {
                    return 1.0 / 100; // 10ms granularity
                }
            })
            .mmsS(IP_MTU - DRASYL_HDR_SIZE)
            .mmsR(IP_MTU - DRASYL_HDR_SIZE)
            .build();

    public static Builder newBuilder() {
        return DEFAULT.toBuilder();
    }

    public abstract IntSupplier unusedPortSupplier();

    public abstract LongSupplier issSupplier();

    public abstract Function<Channel, SendBuffer> sndBufSupplier();

    public abstract Function<Channel, RetransmissionQueue> rtnsQSupplier();

    public abstract Supplier<ReceiveBuffer> rcfBufSupplier();

    public abstract BiFunction<ConnectionConfig, Channel, TransmissionControlBlock> tcbSupplier();

    public abstract boolean activeOpen();

    public abstract int rmem();

    public abstract Duration msl();

    public abstract Duration userTimeout();

    public abstract boolean noDelay();

    public abstract boolean timestamps();

    public abstract double alpha();

    public abstract double beta();

    public abstract int k();

    public abstract Clock clock();

    public abstract Duration lBound();

    public abstract Duration uBound();

    public abstract Duration overrideTimeout();

    public abstract Duration rto();

    public abstract int mmsS();

    public abstract int mmsR();

    public abstract double fs();

    public abstract Builder toBuilder();

    public interface Clock {
        long time();

        // clock granularity in seconds
        double g();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder unusedPortSupplier(final IntSupplier unusedPortSupplier);

        /**
         * Used to choose an initial send sequence number. A random number within [0,4294967296] is
         * chosen by default.
         */
        public abstract Builder issSupplier(final LongSupplier issSupplier);

        /**
         * Used to create the {@link SendBuffer}.
         */
        public abstract Builder sndBufSupplier(final Function<Channel, SendBuffer> sndBufSupplier);

        /**
         * Used to create the {@link RetransmissionQueue}.
         */
        public abstract Builder rtnsQSupplier(final Function<Channel, RetransmissionQueue> rtnsQSupplier);

        /**
         * Used to create the {@link ReceiveBuffer}.
         */
        public abstract Builder rcfBufSupplier(final Supplier<ReceiveBuffer> rcfBufSupplier);

        /**
         * Used to create the {@link TransmissionControlBlock}.
         */
        public abstract Builder tcbSupplier(final BiFunction<ConnectionConfig, Channel, TransmissionControlBlock> tcbProvider);

        /**
         * If enabled, a handshake will be issued on
         * {@link io.netty.channel.ChannelInboundHandler#channelActive(ChannelHandlerContext)}.
         * Otherwise, the remote peer must initiate the handshake.
         */
        public abstract Builder activeOpen(final boolean activeOpen);

        /**
         * Defines the receive buffer size (rmem). Refers to the amount of memory allocated on the
         * receiving side of a connection to temporarily store incoming data before it's processed
         * by the application. Increasing the buffer size can improve network performance by
         * reducing the likelihood of packet loss due to buffer overflow. However, it can also
         * increase memory usage on the receiving side and may not always result in significant
         * performance gains. Set to {@code 65535} bytes by default.
         */
        public abstract Builder rmem(final int rmem);

        /**
         * The maximum segment lifetime, the time a segment can exist in the network. According to
         * RFC 9293, arbitrarily defined to be 2 minutes by default.
         */
        public abstract Builder msl(final Duration msl);

        /**
         * If enabled, small data packets are sent without delay, instead of waiting for larger
         * packets to be filled. Disabling can reduce latency and improve the responsiveness of
         * real-time applications, but may increase the overall network traffic.
         *
         * @see #overrideTimeout(Duration)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.7.4">RFC 9293,
         * Section 3.7.4.</a>
         */
        public abstract Builder noDelay(final boolean noDelay);

        /**
         * Defines how long small data packets are delayed at most. According to RFC 9293, the
         * override timeout should be in the range 0.1 - 1.0 seconds. Default value is 0.1 seconds.
         *
         * @see #noDelay(boolean)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.1">RFC 9293,
         * Section 3.8.6.2.1.</a>
         */
        public abstract Builder overrideTimeout(final Duration overrideTimeout);

        /**
         * A constant used by the Nagle algorithm. Recommended value is 1/2. The default is set to
         * this recommendation
         *
         * @see #noDelay(boolean)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.8.6.2.1">RFC 9293,
         * Section 3.8.6.2.1.</a>
         */
        public abstract Builder fs(final double fs);

        /**
         * Defines the duration how long a connection waits for sent data to be acknowledged before
         * the connection is closed.
         */
        public abstract Builder userTimeout(final Duration userTimeout);

        /**
         * Enables the Timestamps option which is used for round-trip time measurements. Used to
         * arrive a reasonable value for the retransmission timeout. If disabled, the timeout
         * specified by {@link #rto(Duration)} is used. Enabled by default.
         *
         * @see #rto(Duration)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc7323.html#section-3">RFC 7323,
         * Section 3.</a>
         */
        public abstract Builder timestamps(final boolean timestamps);

        /**
         * The retransmission timeout (RTO) is the amount of time that a connection waits before
         * retransmitting a packet that has not been acknowledged.
         *
         * @see #timestamps(boolean)
         * @see #lBound(Duration)
         * @see #uBound(Duration)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc7323.html#section-3">RFC 7323,
         * Section 3.</a>
         */
        public abstract Builder rto(final Duration rto);

        /**
         * The minimum value allowed for the retransmission timeout. This is 1 second by default. If
         * {@link #timestamps(boolean)} is disabled, this value is used as static retransmission
         * timeout.
         *
         * @see #timestamps(boolean)
         * @see #rto(Duration)
         * @see #uBound(Duration)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc793#section-3.7">RFC 793,
         * Section 3.7.</a>
         */
        public abstract Builder lBound(final Duration lBound);

        /**
         * The maximum value allowed for the retransmission timeout. This is 1 minute by default.
         *
         * @see #timestamps(boolean)
         * @see #rto(Duration)
         * @see #lBound(Duration)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc793#section-3.7">RFC 793,
         * Section 3.7.</a>
         */
        public abstract Builder uBound(final Duration uBound);

        /**
         * The ALPHA smoothing factor used for congestion control algorithm to calculate the current
         * congestion window size. It determines the weight given to new data when estimating the
         * network's bandwidth.
         * <p>
         * A higher value for the ALPHA parameter leads to more aggressive growth of the congestion
         * window and can result in higher throughput but may also cause more congestion in the
         * network. A lower value for the ALPHA parameter can help to reduce congestion but may also
         * lead to lower throughput.
         * <p>
         * According to RFC 6298, a value of 1/8 is suggested. The default is set to this
         * suggestion.
         *
         * @see #beta(double)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc6298.html#section-2">RFC 6298,
         * Section 2.</a>
         */
        public abstract Builder alpha(final double alpha);

        /**
         * The BETA delay variance factor is used in congestion control algorithm. It's used to
         * adjust the rate at which the congestion window size is decreased when packets are lost or
         * delayed.
         * <p>
         * A higher value for the BETA parameter can cause the connection to be more aggressive in
         * reducing the congestion window, which can help to prevent congestion but may also lead to
         * reduced throughput. A lower value for the BETA parameter can result in less aggressive
         * reduction of the congestion window, which can lead to higher throughput but may also
         * cause more congestion in the network.
         * <p>
         * According to RFC 6298, a value of 1/4 is suggested. The default is set to this
         * suggestion.
         *
         * @see #alpha(double)
         * @see <a href="https://www.rfc-editor.org/rfc/rfc6298.html#section-2">RFC 6298,
         * Section 2.</a>
         */
        public abstract Builder beta(final double beta);

        /**
         * The K constant is used to scale the RTT variance to calculate the retransmission
         * timeout.
         * <p>
         * According to RFC 6298, a value of 4 is suggested. The default is set to this suggestion.
         *
         * @see <a href="https://www.rfc-editor.org/rfc/rfc6298.html#section-2">RFC 6298,
         * Section 2.</a>
         */
        public abstract Builder k(final int k);

        /**
         * Defines the clock used as time source.
         */
        public abstract Builder clock(final Clock clock);

        /**
         * The maximum segment size for a drasyl-layer message that a connection may send. This
         * value is the IP layer MTU (drasyl assumes a MTU of 1460) minus the drasyl header size. So
         * this value specifies the segment size sent to the wire (including header).
         *
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.7.1">RFC 9293,
         * Section 3.7.1.</a>
         */
        public abstract Builder mmsS(final int mmsS);

        /**
         * The maximum size for a drasyl-layer message that can be received. This value is the IP
         * layer MTU (drasyl assumes a MTU of 1460) minus the drasyl header size. So this value
         * specifies the segment size sent to the wire (including header).
         *
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9293.html#section-3.7.1">RFC 9293,
         * Section 3.7.1.</a>
         */
        public abstract Builder mmsR(final int mmsR);

        abstract ConnectionConfig autoBuild();

        public ConnectionConfig build() {
            return autoBuild();
        }
    }
}
