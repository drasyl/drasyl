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
package org.drasyl.node.handler.serialization;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.annotation.Nullable;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.util.UnsignedShort;

import java.nio.charset.StandardCharsets;

/**
 * This class models the serialized payload of a drasyl application message.
 */
@AutoValue
public abstract class SerializedPayload {
    public static final int MIN_LENGTH = 2;

    @Nullable
    public abstract String getType();

    public abstract byte[] getPayload();

    /**
     * Writes this message to the buffer {@code byteBuf}.
     *
     * @param byteBuf writes this message to the given buffer
     */
    public void writeTo(final ByteBuf byteBuf) {
        final ByteBuf typeBuffer = Unpooled.buffer();

        if (getType() == null) {
            byteBuf.writeBytes(UnsignedShort.of(0).toBytes())
                    .writeBytes(getPayload());
        }
        else {
            byteBuf.writeBytes(UnsignedShort.of(typeBuffer.writeCharSequence(getType(), StandardCharsets.UTF_8)).toBytes())
                    .writeBytes(typeBuffer)
                    .writeBytes(getPayload());
        }

        typeBuffer.release();
    }

    public static SerializedPayload of(final String type, final byte[] payload) {
        return new AutoValue_SerializedPayload(type, payload);
    }

    public static SerializedPayload of(final ByteBuf byteBuf) throws InvalidMessageFormatException {
        if (byteBuf.readableBytes() < MIN_LENGTH) {
            throw new InvalidMessageFormatException("SerializedPayload requires " + MIN_LENGTH + " readable bytes. Only " + byteBuf.readableBytes() + " left.");
        }

        final int stringLength;
        String type = null;
        final byte[] payload;

        stringLength = byteBuf.readUnsignedShort();
        if (stringLength > 0) {
            type = byteBuf.readCharSequence(stringLength, StandardCharsets.UTF_8).toString();
        }
        payload = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(payload);

        return of(type, payload);
    }
}
