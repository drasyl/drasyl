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
package org.drasyl.handler.remote.protocol;

import io.netty.buffer.ByteBuf;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;

/**
 * Describes a message that is sent to remote peers via UDP/TCP.
 */
public interface RemoteMessage {
    int MAGIC_NUMBER = 22527 * 22527;

    Nonce getNonce();

    int getNetworkId();

    IdentityPublicKey getSender();

    ProofOfWork getProofOfWork();

    IdentityPublicKey getRecipient();

    HopCount getHopCount();

    boolean getArmed();

    /**
     * Returns a copy of this message with incremented {@link #getHopCount()}.
     *
     * @return message with incremented hop count
     * @throws IllegalStateException if incremented hop count is greater then {@link
     *                               HopCount#MAX_HOP_COUNT}
     */
    RemoteMessage incrementHopCount();

    /**
     * Writes this message to the buffer {@code out}.
     *
     * @param out writes this envelope to this buffer
     * @throws InvalidMessageFormatException if message could not be written to the given buffer
     */
    void writeTo(final ByteBuf out) throws InvalidMessageFormatException;
}
