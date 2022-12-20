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
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.ProofOfWork;

/**
 * Describes a message that is sent to remote peers via UDP/TCP.
 * <p>
 * Each message is made up of several parts:
 * <ul>
 *     <li>A fixed-length magic number used to identity if message belongs to the drasyl protocol.</li>
 *     <li>A fixed-length public header with partly authenticated information required for routing the message to its destination.</li>
 *     <li>An armed-dependent fixed-length private header with encrypted information only readable by the recipient.</li>
 *     <li>A variable-length body with message type specific (encrypted) information.</li>
 * </ul>
 * <pre>
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     |               Magic Number (4 Bytes)                |
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     |      {@link PublicHeader} ({@link PublicHeader#LENGTH} Bytes)       |
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     |       {@link PrivateHeader} ({@link PrivateHeader#LENGTH} or        |
 *     |          {@link PrivateHeader#ARMED_LENGTH} Bytes)          |
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     /     Body ({@link HelloMessage}, {@link ApplicationMessage},     /
 *     \      {@link AcknowledgementMessage}, or {@link UniteMessage})       \
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 */
@SuppressWarnings("java:S2047")
public interface RemoteMessage {
    int MAGIC_NUMBER = 22527 * 22527;
    int MAGIC_NUMBER_LEN = Integer.BYTES;

    Nonce getNonce();

    int getNetworkId();

    DrasylAddress getSender();

    ProofOfWork getProofOfWork();

    DrasylAddress getRecipient();

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
     */
    void writeTo(final ByteBuf out);

    int getLength();
}
