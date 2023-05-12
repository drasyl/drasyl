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

@AutoValue
public abstract class SNTPMessage {
    public static final int SIZE = 48;
    public static final int VERSION_NUMBER = 4;
    public static final int CLIENT_MODE = 3;
    public static final int SERVER_MODE = 4;
    /**
     * NTP server is not synchronized. Typically, we do not want to use this kind of servers.
     */
    public static final int LI_NOT_SYNC = 3;
    public static final int TRANSMIT_TIMESTAMP_OFFSET = 40;
    /**
     * Reference NTP time if msb equals 0 is 7-Feb-2036 @ 06:28:16 UTC
     */
    private static final long msb0ReferenceTime = 2085978496000L;
    /**
     * Reference NTP time if msb equals 1 is 1-Jan-1900 @ 01:00:00 UTC
     */
    private static final long msb1ReferenceTime = -2208988800000L;

    public static SNTPMessage of(final int leapIndicator,
                                 final int versionNumber,
                                 final int mode,
                                 final int stratum,
                                 final int poll,
                                 final int precision,
                                 final float rootDelay,
                                 final float rootDispersion,
                                 final int referenceIdentifier,
                                 final long referenceTimestamp,
                                 final long originateTimestamp,
                                 final long receiveTimestamp,
                                 final long transmitTimestamp) {
        return new AutoValue_SNTPMessage(leapIndicator, versionNumber, mode, stratum, poll, precision, rootDelay, rootDispersion, referenceIdentifier, referenceTimestamp, originateTimestamp, receiveTimestamp, transmitTimestamp);
    }

    /**
     * Creates a client {@link SNTPMessage}.
     *
     * @param transmitTimestamp the current timestamp in java format.
     * @return a client {@link SNTPMessage}.
     */
    public static SNTPMessage of(final long transmitTimestamp) {
        return of(0, VERSION_NUMBER, CLIENT_MODE, 0, 0, 0, 0.0f, 0.0f, 0, 0, 0, 0, transmitTimestamp);
    }

    public abstract int getLeapIndicator();

    public abstract int getVersionNumber();

    public abstract int getMode();

    public abstract int getStratum();

    public abstract int getPoll();

    public abstract int getPrecision();

    public abstract float getRootDelay();

    public abstract float getRootDispersion();

    public abstract int getReferenceIdentifier();

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
        final boolean base1 = javaTime < msb0ReferenceTime;
        final long baseTime; // time in ms

        if (base1) { // <= Feb-2036
            baseTime = javaTime - msb1ReferenceTime;
        }
        else { // >= Feb-2036
            baseTime = javaTime - msb0ReferenceTime;
        }

        long seconds = baseTime / 1000L;
        final long fraction = ((baseTime % 1000L) * 0x100000000L) / 1000L;

        if (base1) {
            seconds |= 0x80000000L;
        }

        return seconds << 32 | fraction;
    }

    /**
     * Converts {@code ntpTime} to the java time format.
     *
     * @param ntpTime time in format as defined in RFC-1305
     * @return time in java format
     */
    public static long toJavaTime(final long ntpTime) {
        final long seconds = (ntpTime >>> 32) & 0xFFFFFFFFL;
        long fraction = ntpTime & 0xFFFFFFFFL;

        fraction = Math.round((1000.0 * fraction) / 0x100000000L);

        if ((seconds & 0x80000000L) == 0) { // check msb
            return msb0ReferenceTime + (seconds * 1000L) + fraction;
        }
        else {
            return msb1ReferenceTime + (seconds * 1000L) + fraction;
        }
    }
}
