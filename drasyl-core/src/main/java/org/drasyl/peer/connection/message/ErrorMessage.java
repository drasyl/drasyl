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
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing an error. Such an error should always be handled.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ErrorMessage extends AbstractMessage implements RequestMessage, ResponseMessage<RequestMessage> {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final CompressedPublicKey recipient;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final MessageId correspondingId;

    @JsonCreator
    private ErrorMessage(@JsonProperty("id") final MessageId id,
                         @JsonProperty("userAgent") final String userAgent,
                         @JsonProperty("sender") final CompressedPublicKey sender,
                         @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                         @JsonProperty("recipient") final CompressedPublicKey recipient,
                         @JsonProperty("error") final Error error,
                         @JsonProperty("correspondingId") final MessageId correspondingId) {
        super(id, userAgent, sender, proofOfWork);
        this.recipient = recipient;
        this.error = requireNonNull(error);
        this.correspondingId = correspondingId;
    }

    /**
     * Creates a new error message.
     *
     * @param sender          the message's sender
     * @param proofOfWork     sender's proof of work
     * @param recipient       the message's recipient
     * @param error           the error type
     * @param correspondingId the corresponding id of the previous message
     */
    public ErrorMessage(final CompressedPublicKey sender,
                        final ProofOfWork proofOfWork,
                        final CompressedPublicKey recipient,
                        final Error error,
                        final MessageId correspondingId) {
        super(sender, proofOfWork);
        this.recipient = recipient;
        this.error = requireNonNull(error);
        this.correspondingId = correspondingId;
    }

    /**
     * Creates a new error message.
     *
     * @param sender      the message's sender
     * @param proofOfWork sender's proof of work
     * @param error       the error type
     */
    public ErrorMessage(final CompressedPublicKey sender,
                        final ProofOfWork proofOfWork,
                        final Error error) {
        this(sender, proofOfWork, null, error, null);
    }

    /**
     * @return the error
     */
    public Error getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ErrorMessage{" +
                "sender='" + sender + '\'' +
                "proofOfWork='" + proofOfWork + '\'' +
                ", error='" + error + '\'' +
                ", id='" + id +
                '}';
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
        final ErrorMessage that = (ErrorMessage) o;
        return error == that.error;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public CompressedPublicKey getRecipient() {
        return recipient;
    }

    /**
     * Specifies the type of the {@link ErrorMessage}.
     */
    public enum Error {
        ERROR_HANDSHAKE_REJECTED("Handshake has been rejected by other peer."),
        ERROR_IDENTITY_COLLISION("Peer states that my address is already used by another peer with different Public Key."),
        ERROR_WRONG_PUBLIC_KEY("Peer has sent an unexpected Public Key. This could indicate a configuration error or man-in-the-middle attack."),
        ERROR_OTHER_NETWORK("Peer belongs to other network."),
        ERROR_PROOF_OF_WORK_INVALID("The proof of work for the given public key is invalid."),
        ERROR_NOT_A_SUPER_PEER("Peer is not configured as super peer and therefore does not accept children."),
        ERROR_PEER_UNAVAILABLE("Peer is currently not able to accept (new) connections."),
        ERROR_UNEXPECTED_MESSAGE("Unexpected message."),
        ERROR_INITIAL_CHUNK_MISSING("Dropped chunked message because start chunk was not sent."),
        ERROR_CHUNKED_MESSAGE_TIMEOUT("Dropped chunked message because timeout has expired."),
        ERROR_CHUNKED_MESSAGE_PAYLOAD_TOO_LARGE("Dropped chunked message because payload is bigger than the allowed content length."),
        ERROR_CHUNKED_MESSAGE_INVALID_CHECKSUM("Dropped chunked message because checksum was invalid"),
        ERROR_INVALID_SIGNATURE("Signature of the message was invalid.");
        private static final Map<String, Error> errors = new HashMap<>();

        static {
            for (final Error description : values()) {
                errors.put(description.getDescription(), description);
            }
        }

        private final String description;

        Error(final String description) {
            this.description = description;
        }

        /**
         * @return a human readable representation of the reason.
         */
        @JsonValue
        public String getDescription() {
            return description;
        }

        @JsonCreator
        public static Error from(final String description) {
            return errors.get(description);
        }
    }
}