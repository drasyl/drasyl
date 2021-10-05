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
package org.drasyl.handler.crypto;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.identity.KeyAgreementPublicKey;

@AutoValue
public abstract class KeyExchangeMessage extends ArmMessage {
    public static final int LENGTH = 32;

    public abstract KeyAgreementPublicKey getSessionKey();

    @Override
    public void writeBody(final ByteBuf byteBuf) {
        byteBuf.writeBytes(getSessionKey().toByteArray());
    }

    public static KeyExchangeMessage of(final KeyAgreementPublicKey sessionKey) {
        return new AutoValue_KeyExchangeMessage(MessageType.KEY_EXCHANGE, sessionKey);
    }

    public static KeyExchangeMessage of(final ByteBuf byteBuf) throws InvalidMessageFormatException {
        if (byteBuf.readableBytes() < LENGTH) {
            throw new InvalidMessageFormatException("KeyExchangeMessage requires " + LENGTH + " readable bytes. Only " + byteBuf.readableBytes() + " left.");
        }

        final byte[] sessionKeyBuffer = new byte[KeyAgreementPublicKey.KEY_LENGTH_AS_BYTES];
        byteBuf.readBytes(sessionKeyBuffer);

        return of(KeyAgreementPublicKey.of(sessionKeyBuffer));
    }
}
