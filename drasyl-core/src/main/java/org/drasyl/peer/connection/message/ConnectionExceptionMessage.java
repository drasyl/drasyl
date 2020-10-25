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
 * A message representing an exception that refers to a connection. The connection should be
 * terminated after such a message. Such an exception should always be handled.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ConnectionExceptionMessage extends AbstractMessage implements RequestMessage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;

    @JsonCreator
    private ConnectionExceptionMessage(@JsonProperty("id") final MessageId id,
                                       @JsonProperty("userAgent") final String userAgent,
                                       @JsonProperty("sender") final CompressedPublicKey sender,
                                       @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                                       @JsonProperty("error") final Error error) {
        super(id, userAgent, sender, proofOfWork);
        this.error = requireNonNull(error);
    }

    /**
     * Creates a new exception message.
     *
     * @param sender      the message's sender
     * @param proofOfWork sender's proof of work
     * @param error       the exception type
     */
    public ConnectionExceptionMessage(final CompressedPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final Error error) {
        super(sender, proofOfWork);
        this.error = requireNonNull(error);
    }

    /**
     * @return the exception
     */
    public Error getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ConnectionExceptionMessage{" +
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
        final ConnectionExceptionMessage that = (ConnectionExceptionMessage) o;
        return error == that.error;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
    }

    /**
     * Specifies the type of the {@link ConnectionExceptionMessage}.
     */
    public enum Error {
        CONNECTION_ERROR_INITIALIZATION("Error occurred during initialization stage."),
        CONNECTION_ERROR_INTERNAL("Internal Error occurred."),
        CONNECTION_ERROR_HANDSHAKE_TIMEOUT("Handshake did not take place within timeout."),
        CONNECTION_ERROR_HANDSHAKE_REJECTED("Handshake has been rejected by other peer."),
        CONNECTION_ERROR_PING_PONG("Too many Ping Messages were not answered with a Pong Message."),
        CONNECTION_ERROR_IDENTITY_COLLISION("Peer states that my address is already used by another peer with different Public Key."),
        CONNECTION_ERROR_WRONG_PUBLIC_KEY("Peer has sent an unexpected Public Key. This could indicate a configuration error or man-in-the-middle attack."),
        CONNECTION_ERROR_OTHER_NETWORK("Peer belongs to other network."),
        CONNECTION_ERROR_PROOF_OF_WORK_INVALID("The proof of work for the given public key is invalid."),
        CONNECTION_ERROR_NOT_A_SUPER_PEER("Peer is not configured as super peer and therefore does not accept children."),
        CONNECTION_ERROR_PEER_UNAVAILABLE("Peer is currently not able to accept (new) connections.");
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