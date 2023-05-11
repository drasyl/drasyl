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
package org.drasyl.handler.sntp;

import com.google.auto.value.AutoValue;

/**
 * SNTP message: 1                   2                   3 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0
 * 1 2 3 4 5 6 7 8 9 0 1 +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |LI | VN
 * |Mode |    Stratum    |     Poll      |   Precision   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |                          Root
 * Delay                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |                       Root
 * Dispersion                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |                     Reference
 * Identifier                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | | | Reference Timestamp (64)
 * | |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | | | Originate Timestamp (64)
 * | |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | | | Receive Timestamp (64) |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | | | Transmit Timestamp (64) |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ |                 Key
 * Identifier (optional) (32)                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ | | | | | Message Digest
 * (optional) (128)               | | | | |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
@AutoValue
public abstract class SNTPMessage {
    public static final int SIZE = 48;
    public static final int CLIENT_MODE = 3;
    public static final int SERVER_MODE = 4;
    /*
     * NTP server is not synchronized. Typically,
     * we do not want to use this kind of servers.
     */
    public static final int LI_NOT_SYNC = 3;
    public static final int TRANSMIT_TIMESTAMP_OFFSET = 40;

    public abstract int getLeapIndicator();

    public abstract int getVersionNumber();

    public abstract int getMode();

    public abstract int getStratum();

    public abstract int getPoll();

    public abstract int getPrecision();

    public abstract float getRootDelay();

    public abstract String getReferenceIdentifier();

    public abstract long getReferenceTimestamp();

    public abstract long getOriginateTimestamp();

    public abstract long getReceiveTimestamp();

    public abstract long getTransmitTimestamp();

    /**
     * Converts {@code javaTime} to the correct NTP time format as defined in RFC-1305.
     *
     * @param javaTime the time in java format
     * @return time in NTP format as defined in RFC-1305
     */
    public static long toNTPTime(final long javaTime) {

        // TODO: Implement me!

        return 0L;
    }

    /**
     * Converts {@code ntpTime} to the java time format.
     *
     * @param ntpTime time in format as defined in RFC-1305
     * @return time in java format
     */
    public static long toJavaTime(final long ntpTime) {

        // TODO: Implement me!

        return 0L;
    }
}
