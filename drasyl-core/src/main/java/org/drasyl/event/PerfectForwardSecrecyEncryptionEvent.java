/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.event;

/**
 * This event signals, that currently all messages from and to the {@code #peer} are encrypted with
 * an ephemeral session key.
 * <p>
 * The key can be get stale, this means that the connection can fall back to long time encryption in
 * the event of a failed key exchange. In this case a {@link LongTimeEncryptionEvent} is fired.
 */
public class PerfectForwardSecrecyEncryptionEvent extends AbstractPeerEvent {
    /**
     * @param peer the affected peer
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    private PerfectForwardSecrecyEncryptionEvent(final Peer peer) {
        super(peer);
    }

    public static PerfectForwardSecrecyEncryptionEvent of(final Peer peer) {
        return new PerfectForwardSecrecyEncryptionEvent(peer);
    }

    @Override
    public String toString() {
        return "PerfectForwardSecrecyEncryptionEvent{" +
                "peer=" + peer +
                '}';
    }
}
