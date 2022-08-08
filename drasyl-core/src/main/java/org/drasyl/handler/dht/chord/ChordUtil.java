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
package org.drasyl.handler.dht.chord;

import org.drasyl.crypto.Hashing;
import org.drasyl.crypto.HexUtil;

import java.nio.ByteBuffer;

import static org.drasyl.util.Preconditions.requireInRange;

/**
 * Utility class for Chord-related operations.
 */
public final class ChordUtil {
    private static final long[] POWER_OF_TWO = new long[1 + Integer.SIZE];

    static {
        long value = 1;
        for (int i = 0; i < POWER_OF_TWO.length; i++) {
            POWER_OF_TWO[i] = value;
            value *= 2;
        }
    }

    private ChordUtil() {
        // util class
    }

    public static long chordId(final Object o) {
        final int hashCode = o.hashCode();
        final byte[] bytes = ByteBuffer.allocate(Integer.BYTES).putInt(hashCode).array();
        return ByteBuffer.allocate(Long.BYTES).putInt(0).put(Hashing.sha256(bytes), 0, Integer.BYTES).position(0).getLong();
    }

    public static String chordIdHex(final long id) {
        final ByteBuffer buf = ByteBuffer.allocate(Long.BYTES).putLong(id).position(Integer.BYTES);
        final byte[] a = new byte[buf.position()];
        buf.get(a);
        return HexUtil.bytesToHex(a);
    }

    public static String chordIdHex(final Object o) {
        return chordIdHex(chordId(o));
    }

    public static String chordIdPosition(final long id) {
        return id * 100 / POWER_OF_TWO[Integer.SIZE] + "%";
    }

    public static String chordIdPosition(final Object o) {
        return chordIdPosition(chordId(o));
    }

    /**
     * Returns the start id for the {@code i}th finger.
     *
     * @param i between 1 and 32
     */
    public static long ithFingerStart(final long baseId, final int i) {
        return (baseId + POWER_OF_TWO[requireInRange(i, 1, Integer.SIZE) - 1]) % POWER_OF_TWO[Integer.SIZE];
    }

    public static long ithFingerStart(final Object o, final int i) {
        return ithFingerStart(chordId(o), i);
    }

    public static long relativeChordId(final long aId, final long bId) {
        long ret = aId - bId;
        if (ret < 0) {
            ret += POWER_OF_TWO[Integer.SIZE];
        }
        return ret;
    }

    public static long relativeChordId(final Object a, final long b) {
        return relativeChordId(chordId(a), b);
    }

    public static long relativeChordId(final Object a, final Object b) {
        return relativeChordId(a, chordId(b));
    }
}
