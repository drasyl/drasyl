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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.CompressedPublicKey;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Models a chunked application message that can be composed by the recipient to one final message.
 * <br>
 * <br>
 * A chunked message sequence looks like the follow: <br> 1. ChunkedMessage with {@link #recipient},
 * {@link #sender}, {@link #hopCount}, {@link #id}, {@link #payload}, {@link #contentLength} and
 * {@link #checksum} <br> 2. - (n-1). ChunkedMessage {@link #recipient}, {@link #sender}, {@link
 * #hopCount}, {@link #id} and {@link #payload}
 * <br> n. ChunkedMessage {@link #recipient}, {@link #sender}, {@link #hopCount}, {@link #id} and
 * {@link #payload payload := new byte[]{}}
 * <p>
 * This is an immutable object.
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
     * Specifies the checksum of the composite message. Gives the recipient the option
     * to discard damaged messages.
     * <p>
     * Is only included in the first message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String checksum;

    /**
     * Creates a new chunked message.
     *
     * @param sender        the sender of the message
     * @param recipient     the recipient of the message
     * @param id            the id of this message (must be the same as the initial chunk)
     * @param payload       the chunk
     * @param contentLength the final content length
     * @param checksum      the final checksum
     */
    ChunkedMessage(final CompressedPublicKey sender,
                   final CompressedPublicKey recipient,
                   final MessageId id,
                   final byte[] payload,
                   final int contentLength,
                   final String checksum) {
        this(sender, recipient, payload, id, contentLength, checksum, (short) 0);
    }

    @JsonCreator
    private ChunkedMessage(@JsonProperty("sender") final CompressedPublicKey sender,
                           @JsonProperty("recipient") final CompressedPublicKey recipient,
                           @JsonProperty("payload") final byte[] payload,
                           @JsonProperty("id") final MessageId id,
                           @JsonProperty("contentLength") final int contentLength,
                           @JsonProperty("checksum") final String checksum,
                           @JsonProperty("hopCount") final short hopCount) {
        super(id, sender, recipient, payload, hopCount);
        this.contentLength = contentLength;
        this.checksum = checksum;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), contentLength, sender, checksum);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ChunkedMessage that = (ChunkedMessage) o;
        return contentLength == that.contentLength &&
                Arrays.equals(payload, that.payload) &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(checksum, that.checksum);
    }

    @Override
    public String toString() {
        return "ChunkedMessage{" +
                "contentLength=" + contentLength +
                ", payload=byte[" + Optional.ofNullable(payload).orElse(new byte[]{}).length + "] { ... }" +
                ", sender=" + sender +
                ", checksum='" + checksum + '\'' +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                "} ";
    }

    public boolean isInitialChunk() {
        return checksum != null;
    }

    /**
     * Creates the initial chunked message.
     *
     * @param sender        the sender of the message
     * @param recipient     the recipient of the message
     * @param msgID         the id of this message (must be the same as composed message)
     * @param payload       the chunk
     * @param contentLength the final content length
     * @param checksum      the final checksum
     */
    public static ChunkedMessage createFirstChunk(final CompressedPublicKey sender,
                                                  final CompressedPublicKey recipient,
                                                  final MessageId msgID,
                                                  final byte[] payload,
                                                  final int contentLength,
                                                  final String checksum) {
        return new ChunkedMessage(sender, recipient, msgID, payload, contentLength, checksum);
    }

    /**
     * Creates a follow chunked message.
     *
     * @param sender    the sender of the message
     * @param recipient the recipient of the message
     * @param msgID     the id of this message (must be the same as the initial chunk)
     * @param payload   the chunk
     */
    public static ChunkedMessage createFollowChunk(final CompressedPublicKey sender,
                                                   final CompressedPublicKey recipient,
                                                   final MessageId msgID,
                                                   final byte[] payload) {
        return new ChunkedMessage(sender, recipient, msgID, payload, 0, null);
    }

    /**
     * Creates the last chunked message.
     *
     * @param sender    the sender of the message
     * @param recipient the recipient of the message
     * @param msgID     the id of this message (must be the same as the initial chunk)
     */
    public static ChunkedMessage createLastChunk(final CompressedPublicKey sender,
                                                 final CompressedPublicKey recipient,
                                                 final MessageId msgID) {
        return new ChunkedMessage(sender, recipient, msgID, new byte[]{}, 0, null);
    }
}