/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.message;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.HexUtil;
import org.drasyl.pipeline.message.AddressedEnvelope;

import java.util.Arrays;

/**
 * A {@link AddressedEnvelope} is uniquely identified by its 12 bytes identifier.
 * <p>
 * This is an immutable object.
 */
public class MessageId {
    private final byte[] id;

    private MessageId(final byte[] id) {
        if (!isValidMessageId(id)) {
            throw new IllegalArgumentException("ID must be a 12 bit byte array: " + HexUtil.bytesToHex(id));
        }
        this.id = id;
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
        return id;
    }

    /**
     * Static factory to retrieve a randomly generated {@link MessageId}.
     *
     * @return A randomly generated {@code MessageId}
     */
    public static MessageId randomMessageId() {
        return new MessageId(Crypto.randomBytes(12));
    }

    /**
     * Checks if {@code id} is a valid identifier.
     *
     * @param id string to be validated
     * @return {@code true} if valid. Otherwise {@code false}
     */
    public static boolean isValidMessageId(final byte[] id) {
        return id != null && id.length == 12;
    }

    public static MessageId of(final byte[] id) {
        return new MessageId(id);
    }

    public static MessageId of(final String id) {
        return new MessageId(HexUtil.parseHexBinary(id));
    }
}