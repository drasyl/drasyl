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
 * A message representing an exception. Such an exception should always be handled.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings({ "squid:S2166", "common-java:DuplicatedBlocks" })
public class ExceptionMessage extends AbstractMessage {
    private final CompressedPublicKey sender;
    private final ProofOfWork proofOfWork;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Error error;

    @JsonCreator
    private ExceptionMessage(@JsonProperty("id") final MessageId id,
                             @JsonProperty("sender") final CompressedPublicKey sender,
                             @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                             @JsonProperty("error") final Error error) {
        super(id);
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.error = requireNonNull(error);
    }

    /**
     * Creates a new exception message.
     *
     * @param sender      message's sender
     * @param proofOfWork sender's proof of work
     * @param error       the error type
     */
    public ExceptionMessage(final CompressedPublicKey sender,
                            final ProofOfWork proofOfWork,
                            final Error error) {
        super();
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.error = requireNonNull(error);
    }

    /**
     * @return the error
     */
    public Error getError() {
        return error;
    }

    public CompressedPublicKey getSender() {
        return sender;
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public String toString() {
        return "ExceptionMessage{" +
                "sender='" + sender + '\'' +
                ", proofOfWork='" + proofOfWork + '\'' +
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
        final ExceptionMessage that = (ExceptionMessage) o;
        return Objects.equals(sender, that.sender) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                error == that.error;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sender, proofOfWork, error);
    }

    /**
     * Specifies the type of the {@link ExceptionMessage}.
     */
    public enum Error {
        ERROR_INTERNAL("Internal Error occurred."),
        ERROR_FORMAT("Invalid Message format."),
        ERROR_UNEXPECTED_MESSAGE("Unexpected message.");
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