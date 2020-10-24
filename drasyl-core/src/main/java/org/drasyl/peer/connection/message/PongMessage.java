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
 * A message representing a PONG response.
 * <p>
 * This is an immutable object.
 */
public class PongMessage extends AbstractResponseMessage<PingMessage> {
    @JsonCreator
    private PongMessage(@JsonProperty("id") final MessageId id,
                        @JsonProperty("userAgent") final String userAgent,
                        @JsonProperty("networkId") final int networkId,
                        @JsonProperty("sender") final CompressedPublicKey sender,
                        @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                        @JsonProperty("recipient") final CompressedPublicKey recipient,
                        @JsonProperty("correspondingId") final MessageId correspondingId) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, correspondingId);
    }

    public PongMessage(final int networkId,
                       final CompressedPublicKey sender,
                       final ProofOfWork proofOfWork,
                       final CompressedPublicKey recipient,
                       final MessageId correspondingId) {
        super(networkId, sender, proofOfWork, recipient, correspondingId);
    }

    @Override
    public String toString() {
        return "PongMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", correspondingId=" + correspondingId +
                ", id=" + id +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}