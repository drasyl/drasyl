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

import io.netty.util.concurrent.Future;
import org.drasyl.handler.rmi.annotation.RmiTimeout;
import org.drasyl.identity.DrasylAddress;

@RmiTimeout(5_000L)
public interface ChordService {
    /**
     * NOOP method used to check if callee is still alive.
     */
    Future<Void> checkAlive();

    /**
     * Returns the predecessor.
     */
    Future<DrasylAddress> getPredecessor();

    /**
     * Returns the successor.
     */
    Future<DrasylAddress> getSuccessor();

    /**
     * Offers callee to set caller as new predecessor.
     */
    Future<Void> offerAsPredecessor();

    /**
     * Find the closest finger preceding.
     */
    Future<DrasylAddress> findClosestFingerPreceding(final long id);

    /**
     * Find successor for {@code id}.
     */
    Future<DrasylAddress> findSuccessor(final long id);
}
