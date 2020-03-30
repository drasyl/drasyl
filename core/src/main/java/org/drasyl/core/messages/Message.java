/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.messages;

import org.drasyl.core.crypto.CompressedPublicKey;

import java.util.Arrays;
import java.util.Objects;

class Message {
    private final CompressedPublicKey recipient;
    private final byte[] payload;

    public Message(CompressedPublicKey recipient, byte[] payload) {
        this.recipient = recipient;
        this.payload = payload;
    }

    public CompressedPublicKey getRecipient() {
        return recipient;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(recipient);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Message message = (Message) o;
        return Objects.equals(recipient, message.recipient) &&
                Arrays.equals(payload, message.payload);
    }

    @Override
    public String toString() {
        return "Message{" +
                "recipient=" + recipient +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }
}
