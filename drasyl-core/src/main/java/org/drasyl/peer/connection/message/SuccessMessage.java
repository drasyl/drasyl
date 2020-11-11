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

/**
 * Represents a confirmation of a previous sent {@link RequestMessage}.
 */
public class SuccessMessage extends AbstractResponseMessage<RequestMessage> {
    @JsonCreator
    private SuccessMessage(@JsonProperty("id") final MessageId id,
                           @JsonProperty("userAgent") final UserAgent userAgent,
                           @JsonProperty("networkId") final int networkId,
                           @JsonProperty("sender") final CompressedPublicKey sender,
                           @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                           @JsonProperty("recipient") final CompressedPublicKey recipient,
                           @JsonProperty("hopCount") final short hopCount,
                           @JsonProperty("correspondingId") final MessageId correspondingId) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount, correspondingId);
    }

    /**
     * Creates an immutable code object.
     *
     * @param networkId       the network the sender belongs to
     * @param sender          message's sender
     * @param proofOfWork     sender's proof of work
     * @param recipient       message's recipient
     * @param correspondingId the corresponding id of the previous message
     * @throws IllegalArgumentException if the code isn't a valid code code
     */
    public SuccessMessage(final int networkId,
                          final CompressedPublicKey sender,
                          final ProofOfWork proofOfWork,
                          final CompressedPublicKey recipient,
                          final MessageId correspondingId) {
        super(networkId, sender, proofOfWork, recipient, correspondingId);
    }

    @Override
    public String toString() {
        return "SuccessMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", correspondingId=" + correspondingId +
                ", id=" + id +
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
}