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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import static java.util.Objects.requireNonNull;

/**
 * Represents a confirmation of a previous sent {@link RequestMessage}.
 * <p>
 * This is an immutable object.
 */
public class SuccessMessage extends AbstractResponseMessage<RequestMessage> {
    private final CompressedPublicKey recipient;

    @JsonCreator
    private SuccessMessage(@JsonProperty("id") final MessageId id,
                           @JsonProperty("userAgent") final String userAgent,
                           @JsonProperty("sender") final CompressedPublicKey sender,
                           @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                           @JsonProperty("recipient") final CompressedPublicKey recipient,
                           @JsonProperty("correspondingId") final MessageId correspondingId) {
        super(id, userAgent, sender, proofOfWork, correspondingId);
        this.recipient = requireNonNull(recipient);
    }

    /**
     * Creates an immutable code object.
     *
     * @param sender          message's sender
     * @param proofOfWork     sender's proof of work
     * @param recipient       message's recipient
     * @param correspondingId the corresponding id of the previous message
     * @throws IllegalArgumentException if the code isn't a valid code code
     */
    public SuccessMessage(final CompressedPublicKey sender,
                          final ProofOfWork proofOfWork,
                          final CompressedPublicKey recipient,
                          final MessageId correspondingId) {
        super(sender, proofOfWork, correspondingId);
        this.recipient = requireNonNull(recipient);
    }

    @Override
    public String toString() {
        return "OkMessage{" +
                "sender='" + sender + '\'' +
                ", proofOfWork='" + proofOfWork + '\'' +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id +
                '}';
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public CompressedPublicKey getRecipient() {
        return recipient;
    }
}