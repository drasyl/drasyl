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
import org.drasyl.handler.dht.chord.ChordService;
import org.drasyl.handler.rmi.RmiClientHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;

import java.util.Objects;

import static org.drasyl.handler.dht.chord.helper.ChordFillSuccessorHelper.fillSuccessor;
import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public final class ChordDeleteSuccessorHelper {
    private ChordDeleteSuccessorHelper() {
        // util class
    }

    public static FutureComposer<Void> deleteSuccessor(final ChordFingerTable fingerTable,
                                                       final RmiClientHandler client,
                                                       final String serviceName) {
        final DrasylAddress successor = fingerTable.getSuccessor();

        // nothing to delete, just return
        if (successor == null) {
            return composeFuture();
        }

        // find the last existence of successor in the finger table
        int i;
        for (i = Integer.SIZE; i > 0; i--) {
            final DrasylAddress ithFinger = fingerTable.get(i);
            if (ithFinger != null && ithFinger.equals(successor)) {
                break;
            }
        }

        // delete it, from the last existence to the first one
        deleteFromIthToFirstFinger(fingerTable, i, client);

        // if predecessor is successor, delete it
        if (fingerTable.hasPredecessor() && Objects.equals(fingerTable.getPredecessor(), fingerTable.getSuccessor())) {
            fingerTable.removePredecessor();
        }

        // try to fill successor
        return fillSuccessor(fingerTable, client)
                .chain(() -> {
                    final DrasylAddress successor2 = fingerTable.getSuccessor();

                    // if successor is still null or local node,
                    // and the predecessor is another node, keep asking
                    // it's predecessor until find local node's new successor
                    if ((successor2 == null || fingerTable.getLocalAddress().equals(successor2)) && fingerTable.hasPredecessor() && !fingerTable.getLocalAddress().equals(fingerTable.getPredecessor())) {
                        final DrasylAddress predecessor = fingerTable.getPredecessor();

                        return findNewSuccessor(predecessor, successor2, fingerTable, client, serviceName)
                                // update successor
                                .chain(() -> fingerTable.updateIthFinger(1, predecessor, client));
                    }
                    else {
                        return composeFuture();
                    }
                });
    }

    private static FutureComposer<Void> deleteFromIthToFirstFinger(final ChordFingerTable fingerTable,
                                                                   final int j,
                                                                   final RmiClientHandler client) {
        return fingerTable.updateIthFinger(j, null, client)
                .chain(future -> {
                    if (j > 1) {
                        return deleteFromIthToFirstFinger(fingerTable, j - 1, client);
                    }
                    else {
                        return fingerTable.updateIthFinger(j, null, client);
                    }
                });
    }

    private static FutureComposer<DrasylAddress> findNewSuccessor(final DrasylAddress peer,
                                                                  final DrasylAddress successor,
                                                                  final ChordFingerTable fingerTable,
                                                                  final RmiClientHandler client,
                                                                  final String serviceName) {
        final ChordService service = client.lookup(serviceName, ChordService.class, peer);
        return composeFuture().chain(service.yourPredecessor())
                .chain(future -> {
                    DrasylAddress predecessor = future.getNow();
                    if (predecessor == null) {
                        return composeFuture(peer);
                    }

                    // if p's predecessor is node is just deleted,
                    // or itself (nothing found in predecessor), or local address,
                    // p is current node's new successor, break
                    if (predecessor.equals(peer) || predecessor.equals(fingerTable.getLocalAddress()) || predecessor.equals(successor)) {
                        return composeFuture(peer);
                    }
                    // else, keep asking
                    else {
                        return findNewSuccessor(predecessor, successor, fingerTable, client, serviceName);
                    }
                });
    }
}
