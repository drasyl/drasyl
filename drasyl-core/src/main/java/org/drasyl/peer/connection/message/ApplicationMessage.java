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
package org.drasyl.peer.connection.message;

import org.drasyl.identity.Address;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message that is sent by an application running on drasyl.
 */
public class ApplicationMessage extends AbstractMessage implements RequestMessage {
    private final Address recipient;
    private final Address sender;
    private final byte[] payload;

    protected ApplicationMessage() {
        this.recipient = null;
        this.sender = null;
        this.payload = null;
    }

    ApplicationMessage(String id,
                       Address recipient,
                       Address sender,
                       byte[] payload) {
        super(id);
        this.recipient = recipient;
        this.sender = sender;
        this.payload = payload;
    }

    /**
     * Creates a new message.
     *
     * @param sender    The sender
     * @param recipient The recipient
     * @param payload   The data to be sent
     */
    public ApplicationMessage(Address sender, Address recipient, byte[] payload) {
        this.sender = requireNonNull(sender);
        this.recipient = requireNonNull(recipient);
        this.payload = requireNonNull(payload);
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
        if (!(o instanceof ApplicationMessage)) {
            return false;
        }
        ApplicationMessage message = (ApplicationMessage) o;
        return Objects.equals(getId(), message.getId()) &&
                Objects.equals(recipient, message.recipient) &&
                Objects.equals(sender, message.sender) &&
                Arrays.equals(payload, message.payload);
    }

    @Override
    public String toString() {
        return "ApplicationMessage{" +
                "recipient=" + recipient +
                ", sender=" + sender +
                ", payload=" + Arrays.toString(payload) +
                ", id='" + id +
                '}';
    }

    public Address getRecipient() {
        return recipient;
    }

    public Address getSender() {
        return sender;
    }

    public byte[] getPayload() {
        return payload;
    }
}
