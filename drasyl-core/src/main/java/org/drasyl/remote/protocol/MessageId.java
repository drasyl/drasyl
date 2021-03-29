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
package org.drasyl.remote.protocol;

import org.drasyl.annotation.NonNull;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.HexUtil;
import org.drasyl.pipeline.message.AddressedEnvelope;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A {@link AddressedEnvelope} is uniquely identified by its {@link #MESSAGE_ID_LENGTH} bytes
 * identifier.
 * <p>
 * This is an immutable object.
 */
public final class MessageId {
    public static final int MESSAGE_ID_LENGTH = 8;
    private final byte[] id;

    private MessageId(@NonNull final byte[] id) {
        if (!isValidMessageId(id)) {
            throw new IllegalArgumentException("ID must be a " + MESSAGE_ID_LENGTH + " bit byte array: " + HexUtil.bytesToHex(id));
        }
        this.id = id.clone();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MessageId messageId = (MessageId) o;
        return Arrays.equals(id, messageId.id);
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(id);
    }

    public byte[] byteArrayValue() {
        return id.clone();
    }

    public long longValue() {
        return ByteBuffer.wrap(id).getLong();
    }

    /**
     * Static factory to retrieve a randomly generated {@link MessageId}.
     *
     * @return A randomly generated {@code MessageId}
     */
    public static MessageId randomMessageId() {
        return new MessageId(Crypto.randomBytes(MESSAGE_ID_LENGTH));
    }

    /**
     * Checks if {@code id} is a valid identifier.
     *
     * @param id string to be validated
     * @return {@code true} if valid. Otherwise {@code false}
     */
    public static boolean isValidMessageId(final byte[] id) {
        return id != null && id.length == MESSAGE_ID_LENGTH;
    }

    /**
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public static MessageId of(@NonNull final byte[] id) {
        return new MessageId(id);
    }

    /**
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public static MessageId of(@NonNull final String id) {
        return new MessageId(HexUtil.parseHexBinary(id));
    }

    public static MessageId of(final long id) {
        return of(ByteBuffer.allocate(Long.BYTES).putLong(id).array());
    }
}
