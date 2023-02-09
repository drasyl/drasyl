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

import java.time.Duration;

import static java.time.Duration.ofMinutes;

@AutoValue
public abstract class ReliableTransportConfig {
    public static Builder newBuilder() {
        final int baseMss = 1432;
        // RFC 9293: Arbitrarily defined to be 2 minutes.
        final Duration msl = ofMinutes(2);
        return new AutoValue_ReliableTransportConfig.Builder().baseMss(baseMss).msl(msl);
    }

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
    public abstract boolean noDelay();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder baseMss(final int baseMss);

        public abstract Builder rmem(final int rmem);

        public abstract Builder msl(final Duration msl);

        public abstract Builder noDelay(final boolean noDelay);

        abstract ReliableTransportConfig autoBuild();

        public ReliableTransportConfig build() {
            return autoBuild();
        }
    }
}
