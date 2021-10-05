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

import com.goterl.lazysodium.utils.SessionPair;
import io.netty.buffer.ByteBuf;
import org.drasyl.crypto.Crypto;

import java.io.IOException;

/**
 * Skeleton implementation for a {@link FullReadMessage}.
 */
abstract class AbstractFullReadMessage<T extends FullReadMessage<?>> implements FullReadMessage<T> {
    @Override
    public ArmedProtocolMessage arm(final ByteBuf byteBuf, final Crypto cryptoInstance,
                                    final SessionPair sessionPair) throws InvalidMessageFormatException {
        try {
            writePrivateHeaderTo(byteBuf);
            writeBodyTo(byteBuf);

            final UnarmedProtocolMessage unarmedMessage = UnarmedProtocolMessage.of(
                    getHopCount(), true, getNetworkId(), getNonce(),
                    getRecipient(), getSender(),
                    getProofOfWork(),
                    byteBuf);
            return unarmedMessage.armAndRelease(cryptoInstance, sessionPair);
        }
        catch (final IOException e) {
            byteBuf.release();
            throw new InvalidMessageFormatException("Unable to arm message", e);
        }
    }

    @Override
    public void writeTo(final ByteBuf out) {
        // message (partially) present as java objects. get bytes and transfer to buffer
        out.writeInt(MAGIC_NUMBER);
        writePublicHeaderTo(out);
        writePrivateHeaderTo(out);
        writeBodyTo(out);
    }

    protected void writePublicHeaderTo(final ByteBuf out) {
        PublicHeader.of(this)
                .writeTo(out);
    }

    protected abstract void writePrivateHeaderTo(ByteBuf out);

    protected abstract void writeBodyTo(ByteBuf out);
}
