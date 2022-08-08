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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.handler.dht.chord.ChordUtil.chordId;
import static org.drasyl.handler.dht.chord.ChordUtil.chordIdHex;
import static org.drasyl.handler.dht.chord.ChordUtil.relativeChordId;
import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.FutureComposer.composeSucceededFuture;

/**
 * Helper class providing {@code n.closest_preceding_finger(id)} functionality.
 * <p>
 * This class is based on <a href="https://github.com/ChuanXia/Chord">Chord implementation of Chuan
 * Xia</a>.
 */
public final class ChordClosestPrecedingFingerHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ChordClosestPrecedingFingerHelper.class);

    private ChordClosestPrecedingFingerHelper() {
        // util class
    }

    public static FutureComposer<DrasylAddress> closestPrecedingFinger(final long findId,
                                                                       final ChordFingerTable fingerTable,
                                                                       final RmiClientHandler client,
                                                                       final String serviceName) {
        LOG.debug("Find closest finger preceding `{}`.", chordIdHex(findId));
        final long myId = chordId(fingerTable.getLocalAddress());
        final long findIdRelativeId = relativeChordId(findId, myId);

        // check from last item in finger table
        return checkFinger(myId, findId, findIdRelativeId, Integer.SIZE, fingerTable, client, serviceName);
    }

    @SuppressWarnings("java:S3776")
    private static FutureComposer<DrasylAddress> checkFinger(final long myId,
                                                             final long findId,
                                                             final long findIdRelative,
                                                             final int i,
                                                             final ChordFingerTable fingerTable,
                                                             final RmiClientHandler client,
                                                             final String serviceName) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", chordIdHex(findId));
            return composeSucceededFuture(fingerTable.getLocalAddress());
        }
        else {
            return composeSucceededFuture(fingerTable.get(i)).then(future -> {
                final DrasylAddress ithFinger = future.getNow();
                if (ithFinger != null) {
                    // if its relative id is the closest, check if its alive
                    final long ithFingerId = chordId(ithFinger);
                    final long ithFingerRelativeId = relativeChordId(ithFingerId, myId);

                    if (ithFingerRelativeId > 0 && ithFingerRelativeId < findIdRelative) {
                        LOG.debug("{}th finger {} is closest preceding finger of {}.", i, chordIdHex(ithFingerId), chordIdHex(findId));
                        LOG.debug("Check if it is still alive.");

                        final ChordService service = client.lookup(serviceName, ChordService.class, ithFinger);
                        return composeFuture(service.keep()).then(future2 -> {
                            //it is alive, return it
                            if (future2.isSuccess()) {
                                LOG.debug("Peer is still alive.");
                                return composeSucceededFuture(ithFinger);
                            }
                            // else, remove its existence from finger table
                            else {
                                LOG.warn("Peer `{}` is not alive. Remove it from finger table.", ithFinger);
                                fingerTable.removePeer(ithFinger);
                            }
                            return checkFinger(myId, findId, findIdRelative, i - 1, fingerTable, client, serviceName);
                        });
                    }
                }
                return checkFinger(myId, findId, findIdRelative, i - 1, fingerTable, client, serviceName);
            });
        }
    }
}
