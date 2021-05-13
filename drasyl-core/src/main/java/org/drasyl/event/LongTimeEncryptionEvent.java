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
 * This event signals, that currently all messages from and to the {@code #peer} are <b>only</b>
 * encrypted with a long time key. The default case is long time key encryption.
 * <p>
 * For more secure communication, the application have to wait until the {@link
 * PerfectForwardSecrecyEncryptionEvent} is fired.
 */
public class LongTimeEncryptionEvent extends AbstractPeerEvent {
    /**
     * @param peer the affected peer
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    private LongTimeEncryptionEvent(final Peer peer) {
        super(peer);
    }

    public static LongTimeEncryptionEvent of(final Peer peer) {
        return new LongTimeEncryptionEvent(peer);
    }

    @Override
    public String toString() {
        return "LongTimeEncryptionEvent{" +
                "peer=" + peer +
                '}';
    }
}
