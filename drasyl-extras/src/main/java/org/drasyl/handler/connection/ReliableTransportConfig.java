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

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

@AutoValue
public abstract class ReliableTransportConfig {
    public static final ReliableTransportConfig DEFAULT = new AutoValue_ReliableTransportConfig.Builder()
            .issSupplier(Segment::randomSeq)
            .sndBufSupplier(SendBuffer::new)
            .rtnsQSupplier(RetransmissionQueue::new)
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
                    config.rcfBufSupplier().apply(channel)
            ))
            .activeOpen(true)
            .baseMss(1432)
            .rmem(64 * 1432)
            // RFC 9293: Arbitrarily defined to be 2 minutes.
            .msl(ofMinutes(2))
            .noDelay(false)
            .userTimeout(ofSeconds(60))
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

        abstract ReliableTransportConfig autoBuild();

        public ReliableTransportConfig build() {
            return autoBuild();
        }
    }
}
