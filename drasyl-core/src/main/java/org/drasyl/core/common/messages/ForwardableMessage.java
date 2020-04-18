/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.core.common.messages;

import org.drasyl.core.common.models.SessionUID;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message that is forwarded from the relay server to
 * {@link ForwardableMessage#receiverUID receiverUID} when it is reachable. The
 * Response will be a {@link Status#OK} if the message recipients could be found
 * in the network or if the message was a broadcast via {@link SessionUID#ALL}
 * or {@link SessionUID#MULTICAST_DELIMITER}.
 * Otherwise, the Response will be {@link Status#NOT_FOUND}.
 */
public class ForwardableMessage extends AbstractMessage {
    private final SessionUID senderUID;
    private final SessionUID receiverUID;
    private final byte[] blob;

    protected ForwardableMessage() {
        this.senderUID = null;
        this.blob = null;
        this.receiverUID = null;
    }

    /**
     * Private constructor to create a new ForwardableMessage, with the same blob
     * and sender UID as the old one, but with a new receiver UID.
     *
     * <p>
     * Currently used for substitution of ALL- and ANY-Addresses to real client
     * UIDs.
     * </p>
     *
     * @param msg         message that should be copied
     * @param receiverUID new receiver UID of the message
     * @since 1.5.0-SNAPSHOT
     */
    private ForwardableMessage(ForwardableMessage msg, SessionUID receiverUID) {
        super(msg.getMessageID());
        this.senderUID = msg.getSenderUID();
        this.blob = msg.getBlob();
        this.receiverUID = receiverUID;
    }

    /**
     * Creates a new forwardable message.
     *
     * @param senderUID   The sender UID
     * @param receiverUID The receiver UID
     * @param blob        The data to send
     */
    public ForwardableMessage(SessionUID senderUID, SessionUID receiverUID, byte[] blob) {
        this.senderUID = requireNonNull(senderUID);
        this.receiverUID = requireNonNull(receiverUID);
        this.blob = blob;

        if (senderUID.getUIDs().size() > 1)
            throw new IllegalArgumentException("The sender uid can't be a multicast address.");
    }

    /**
     * @return the senderUID
     */
    public SessionUID getSenderUID() {
        return senderUID;
    }

    /**
     * @return the receiverUID
     */
    public SessionUID getReceiverUID() {
        return receiverUID;
    }

    /**
     * @return the blob
     */
    public byte[] getBlob() {
        return blob;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ForwardableMessage)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ForwardableMessage that = (ForwardableMessage) o;
        return Objects.equals(getSenderUID(), that.getSenderUID()) &&
                Objects.equals(getReceiverUID(), that.getReceiverUID()) &&
                Arrays.equals(getBlob(), that.getBlob());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getSenderUID(), getReceiverUID(), Arrays.hashCode(getBlob()));
    }

    /**
     * Returns a new ForwardableMessage object with a new receiverUID.
     *
     * @param oldMessage  the old forwardable message
     * @param receiverUID the new receiver UID
     * @since 1.5.0-SNAPSHOT
     */
    public static ForwardableMessage to(ForwardableMessage oldMessage, SessionUID receiverUID) {
        return new ForwardableMessage(oldMessage, receiverUID);
    }

    @Override
    public String toString() {
        return "ForwardableMessage [senderUID=" + senderUID + ", receiverUID=" + receiverUID + ", blob=..., messageID=" + getMessageID() + "]";
    }

    public String toFullString() {
        return "ForwardableMessage [senderUID=" + senderUID + ", receiverUID=" + receiverUID + ", blob=" + blob
                + ", messageID=" + getMessageID() + "]";
    }

}
