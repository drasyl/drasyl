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

import org.drasyl.core.crypto.Signable;
import org.drasyl.core.crypto.Signature;
import org.drasyl.core.models.Identity;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

class Message implements Signable {
    private final Identity recipient;
    private final Identity sender;
    private Signature signature;
    private final byte[] payload;

    protected Message() {
        recipient = null;
        sender = null;
        signature = null;
        payload = null;
    }

    public Message(Identity sender, Identity recipient, byte[] payload) {
        this.sender = sender;
        this.recipient = recipient;
        this.payload = payload;
    }

    public Identity getRecipient() {
        return recipient;
    }

    public Identity getSender() {
        return sender;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Signature getSignature() {
        return signature;
    }

    @Override
    public void writeFieldsTo(OutputStream outstream) throws IOException {
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(payload);

        outstream.write(recipient.toString().getBytes(StandardCharsets.UTF_8));
        outstream.write(sender.toString().getBytes(StandardCharsets.UTF_8));
        outstream.write(payload);
    }

    @Override
    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(recipient);
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
                "sender = " + sender +
                ", payload=" + Arrays.toString(payload) +
                "}";
    }
}
