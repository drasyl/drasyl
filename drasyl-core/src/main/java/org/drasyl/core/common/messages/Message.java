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
package org.drasyl.core.common.messages;

import org.drasyl.core.node.identity.Identity;
import org.drasyl.crypto.Signature;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A message that is forwarded from the node server to {@link Message#recipient recipient}.
 */
public class Message extends AbstractMessage {
    private final Identity recipient;
    private final Identity sender;
    private final byte[] payload;

    protected Message() {
        this.recipient = null;
        this.sender = null;
        this.payload = null;
    }

    protected Message(String msgID,
                      Signature signature,
                      Identity recipient,
                      Identity sender,
                      byte[] payload) {
        super(msgID);
        setSignature(signature);
        this.recipient = recipient;
        this.sender = sender;
        this.payload = payload;
    }

    protected static Message copyOf(Message message) {
        return new Message(message.getMessageID(), message.getSignature(), message.getRecipient(), message.getSender(), message.getPayload());
    }

    /**
     * Creates a new message.
     *
     * @param sender    The sender
     * @param recipient The recipient
     * @param payload   The data to be sent
     */
    public Message(Identity sender, Identity recipient, byte[] payload) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(payload);

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

    @Override
    public void writeFieldsTo(OutputStream outstream) throws IOException {
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(sender);
        Objects.requireNonNull(payload);

        outstream.write(getMessageID().getBytes(StandardCharsets.UTF_8));
        outstream.write(recipient.toString().getBytes(StandardCharsets.UTF_8));
        outstream.write(sender.toString().getBytes(StandardCharsets.UTF_8));
        outstream.write(payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), recipient, sender);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message)) {
            return false;
        }
        Message message = (Message) o;
        return Objects.equals(getMessageID(), message.getMessageID()) &&
                Objects.equals(recipient, message.recipient) &&
                Objects.equals(sender, message.sender) &&
                Objects.equals(getSignature(), message.getSignature()) &&
                Arrays.equals(payload, message.payload);
    }

    @Override
    public String toString() {
        return "Message{" +
                "recipient=" + recipient +
                ", sender = " + sender +
                ", payload=" + Arrays.toString(payload) +
                ", messageID=" + getMessageID() +
                ", signature=" + getSignature() +
                "}";
    }
}
