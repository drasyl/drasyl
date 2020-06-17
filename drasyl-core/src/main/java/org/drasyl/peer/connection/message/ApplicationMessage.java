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

import org.drasyl.identity.Identity;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message that is sent by an application running on drasyl.
 */
public class ApplicationMessage extends RelayableMessage implements RequestMessage {
    private final Identity sender;
    private final byte[] payload;

    protected ApplicationMessage() {
        super();
        this.sender = null;
        this.payload = null;
    }

    ApplicationMessage(String id,
                       Identity recipient,
                       Identity sender,
                       byte[] payload,
                       short hopCount) {
        super(id, hopCount, recipient);
        this.sender = sender;
        this.payload = payload;
    }

    /**
     * Creates a new message.
     *  @param sender    The sender
     * @param recipient The recipient
     * @param payload   The data to be sent
     */
    public ApplicationMessage(Identity sender, Identity recipient, byte[] payload) {
        this(sender, recipient, payload, (short) 0);
    }

    ApplicationMessage(Identity sender, Identity recipient, byte[] payload, short hopCount) {
        super(recipient, hopCount);
        this.sender = requireNonNull(sender);
        this.payload = requireNonNull(payload);
    }

    public Identity getSender() {
        return sender;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), sender);
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
        if (!super.equals(o)) {
            return false;
        }
        ApplicationMessage that = (ApplicationMessage) o;
        return Objects.equals(sender, that.sender) &&
                Arrays.equals(payload, that.payload);
    }

    @Override
    public String toString() {
        return "ApplicationMessage{" +
                "sender=" + sender +
                ", payload=" + Arrays.toString(payload) +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                '}';
    }
}
