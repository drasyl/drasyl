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
package org.drasyl.handler.dht.chord.helper;

import org.drasyl.handler.dht.chord.ChordFingerTable;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public final class ChordFillSuccessorHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFillSuccessorHelper.class);

    private ChordFillSuccessorHelper() {
        // util class
    }

    public static FutureComposer<Void> fillSuccessor(final ChordFingerTable fingerTable,
                                                     final RmiClientHandler client) {
        LOG.debug("Try to fill successor with candidates in finger table or even predecessor.");
        final DrasylAddress successor = fingerTable.getSuccessor();
        final FutureComposer<Void> future;
        if (successor == null || successor.equals(fingerTable.getLocalAddress())) {
            future = findSuccessorStartingFromIthFinger(2, fingerTable, client);
        }
        else {
            future = composeFuture();
        }

        return future.chain(() -> {
            final DrasylAddress successor2 = fingerTable.getSuccessor();
            if ((successor2 == null || successor2.equals(fingerTable.getLocalAddress())) && fingerTable.hasPredecessor() && !fingerTable.getLocalAddress().equals(fingerTable.getPredecessor())) {
                return fingerTable.updateIthFinger(1, fingerTable.getPredecessor(), client);
            }
            else {
                return composeFuture();
            }
        });
    }

    private static FutureComposer<Void> findSuccessorStartingFromIthFinger(final int i,
                                                                           final ChordFingerTable fingerTable,
                                                                           final RmiClientHandler client) {
        if (i <= Integer.SIZE) {
            final DrasylAddress ithFinger = fingerTable.get(i);
            if (ithFinger != null && !ithFinger.equals(fingerTable.getLocalAddress())) {
                return updateFingersFromIthToFirstFinger(i - 1, ithFinger, fingerTable, client);
            }
            else {
                return findSuccessorStartingFromIthFinger(i + 1, fingerTable, client);
            }
        }
        else {
            return composeFuture();
        }
    }

    private static FutureComposer<Void> updateFingersFromIthToFirstFinger(final int j,
                                                                          final DrasylAddress ithfinger,
                                                                          final ChordFingerTable fingerTable,
                                                                          final RmiClientHandler client) {
        if (j >= 1) {
            return fingerTable.updateIthFinger(j, ithfinger, client)
                    .chain(() -> updateFingersFromIthToFirstFinger(j - 1, ithfinger, fingerTable, client));
        }
        else {
            return composeFuture();
        }
    }
}
