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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.drasyl.identity.CompressedPublicKey;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Models a chunked application message that can be composed by the recipient to one final message.
 * <br>
 * <br>
 * A chunked message sequence looks like the follow: <br> 1. ChunkedMessage with {@link #recipient},
 * {@link #sender}, {@link #hopCount}, {@link #id}, {@link #payload}, {@link #sequenceNumber},
 * {@link #contentLength} and {@link #checksum} <br> 2. - (n-1). ChunkedMessage {@link #recipient},
 * {@link #sender}, {@link #hopCount}, {@link #id}, {@link #payload} and {@link #sequenceNumber}
 * <br> n. ChunkedMessage {@link #recipient}, {@link #sender}, {@link #hopCount}, {@link #id},
 * {@link #payload payload := new byte[]{}} and {@link #sequenceNumber}
 */
public class ChunkedMessage extends ApplicationMessage {
    /*
     * Specifies how large the composite message will be. Gives the recipient the option
     * to reject a composite message if it is too large.
     * <p>
     * Is only included in the first message.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private final int contentLength;
    /*
     * Specifies the position of this message in the composite message. Allows the
     * recipient to correctly compose the message.
     */
    private final int sequenceNumber;
    /*
     * Specifies the checksum of the composite message. Gives the recipient the option
     * to discard damaged messages.
     * <p>
     * Is only included in the first message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String checksum;

    /**
     * For jackson.
     */
    private ChunkedMessage() {
        this.contentLength = 0;
        this.sequenceNumber = 0;
        this.checksum = null;
    }

    /**
     * Creates a new chunked message.
     *
     * @param sender         the sender of the message
     * @param recipient      the recipient of the message
     * @param msgID          the id of this message (must be the same as the initial chunk)
     * @param payload        the chunk
     * @param contentLength  the final content length
     * @param checksum       the final checksum
     * @param sequenceNumber the sequence number of this message
     */
    protected ChunkedMessage(CompressedPublicKey sender,
                             CompressedPublicKey recipient,
                             String msgID,
                             byte[] payload,
                             int contentLength,
                             String checksum,
                             int sequenceNumber) {
        super(msgID, recipient, sender, payload, (short) 0);
        this.contentLength = contentLength;
        this.checksum = checksum;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Creates the initial chunked message.
     *
     * @param sender        the sender of the message
     * @param recipient     the recipient of the message
     * @param payload       the chunk
     * @param contentLength the final content length
     * @param checksum      the final checksum
     */
    public ChunkedMessage(CompressedPublicKey sender,
                          CompressedPublicKey recipient,
                          byte[] payload,
                          int contentLength,
                          String checksum) {
        super(sender, recipient, payload);
        this.contentLength = contentLength;
        this.sequenceNumber = 0;
        this.checksum = checksum;
    }

    /**
     * Creates a follow chunked message.
     *
     * @param sender         the sender of the message
     * @param recipient      the recipient of the message
     * @param msgID          the id of this message (must be the same as the initial chunk)
     * @param payload        the chunk
     * @param sequenceNumber the sequence number of this message
     */
    public static ChunkedMessage createFollowChunk(CompressedPublicKey sender,
                                                   CompressedPublicKey recipient,
                                                   String msgID,
                                                   byte[] payload,
                                                   int sequenceNumber) {
        return new ChunkedMessage(sender, recipient, msgID, payload, 0, null, sequenceNumber);
    }

    /**
     * Creates the last chunked message.
     *
     * @param sender         the sender of the message
     * @param recipient      the recipient of the message
     * @param msgID          the id of this message (must be the same as the initial chunk)
     * @param sequenceNumber the sequence number of this message
     */
    public static ChunkedMessage createLastChunk(CompressedPublicKey sender,
                                                 CompressedPublicKey recipient,
                                                 String msgID,
                                                 int sequenceNumber) {
        return new ChunkedMessage(sender, recipient, msgID, new byte[]{}, 0, null, sequenceNumber);
    }

    public int getContentLength() {
        return contentLength;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), contentLength, sequenceNumber, sender, checksum);
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
        ChunkedMessage that = (ChunkedMessage) o;
        return contentLength == that.contentLength &&
                sequenceNumber == that.sequenceNumber &&
                Arrays.equals(payload, that.payload) &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(checksum, that.checksum);
    }

    @Override
    public String toString() {
        return "ChunkedMessage{" +
                "contentLength=" + contentLength +
                ", sequenceNumber=" + sequenceNumber +
                ", payload=byte[" + Optional.ofNullable(payload).orElse(new byte[]{}).length + "] { ... }" +
                ", sender=" + sender +
                ", checksum='" + checksum + '\'' +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                "} ";
    }
}
