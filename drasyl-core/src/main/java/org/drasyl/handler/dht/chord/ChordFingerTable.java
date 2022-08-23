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

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdPosition;
import static org.drasyl.handler.dht.chord.ChordUtil.ithFingerStart;

/**
 * Chord routing table.
 */
public class ChordFingerTable {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFingerTable.class);
    protected final DrasylAddress[] entries;
    private final DrasylAddress localAddress;

    public ChordFingerTable(final DrasylAddress localAddress) {
        this.entries = new DrasylAddress[Integer.SIZE];
        this.localAddress = requireNonNull(localAddress);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        // header
        sb.append(String.format("%-4s %-15s %-65s %-15s%n", "No.", "Start", "Address", "Id"));

        // body
        for (int i = 1; i <= entries.length; i++) {
            final long ithStart = ithFingerStart(localAddress, i);
            sb.append(String.format("%2s   %-15s %-65s %-15s%n", i, chordIdHex(ithStart) + " (" + chordIdPosition(ithStart) + ")", entries[i - 1], entries[i - 1] != null ? (chordIdHex(entries[i - 1]) + " (" + chordIdPosition(entries[i - 1]) + ")") : ""));
        }

        return sb.toString();
    }

    public DrasylAddress get(final int i) {
        return entries[i - 1];
    }

    public DrasylAddress getSuccessor() {
        return get(1);
    }

    public boolean hasSuccessor() {
        return getSuccessor() != null;
    }

    public void removePeer(final DrasylAddress value) {
        LOG.info("Remove peer `{}` from finger table.", value);
        for (int i = Integer.SIZE; i > 0; i--) {
            final DrasylAddress ithFinger = entries[i - 1];
            if (ithFinger != null && ithFinger.equals(value)) {
                entries[i - 1] = null;
            }
        }
    }

    /**
     * Updates the {@code i}th finger to point at {@code value}.
     *
     * @param i     finger to update
     * @param value new value
     * @return {@code true} if our successor (1st finger) was updated
     */
    public boolean updateIthFinger(final int i, final DrasylAddress value) {
        final DrasylAddress oldValue = entries[i - 1];
        entries[i - 1] = value;
        final boolean changed = !Objects.equals(value, oldValue);
        if (changed) {
            //noinspection unchecked
            LOG.info("Updated {}th finger to `{}` ({} -> {} -> {}).", () -> String.format("%2s", i), () -> value, () -> oldValue != null ? String.format("%4s", chordIdPosition(oldValue)) : "null", () -> value != null ? String.format("%4s", chordIdPosition(value)) : "null", () -> String.format("%4s", chordIdPosition(ithFingerStart(localAddress, i))));
        }

        // if the updated one is successor, notify the new successor
        return i == 1 && value != null && !value.equals(localAddress) && changed;
    }
}
