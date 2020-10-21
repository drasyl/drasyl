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
import org.drasyl.peer.PeerInformation;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This message is used as a response to a {@link WhoisMessage} and contains information about a
 * peer (like public key and endpoints).
 * <p>
 * This is an immutable object.
 */
public class IdentityMessage extends RelayableMessage implements ResponseMessage<WhoisMessage> {
    private final PeerInformation peerInformation;
    private final MessageId correspondingId;

    public IdentityMessage(final CompressedPublicKey sender,
                           final ProofOfWork proofOfWork,
                           final CompressedPublicKey recipient,
                           final PeerInformation peerInformation,
                           final MessageId correspondingId) {
        super(sender, proofOfWork, recipient);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    @JsonCreator
    private IdentityMessage(@JsonProperty("id") final MessageId id,
                            @JsonProperty("sender") final CompressedPublicKey sender,
                            @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                            @JsonProperty("recipient") final CompressedPublicKey recipient,
                            @JsonProperty("peerInformation") final PeerInformation peerInformation,
                            @JsonProperty("correspondingId") final MessageId correspondingId,
                            @JsonProperty("hopCount") final short hopCount) {
        super(id, sender, proofOfWork, recipient, hopCount);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    public PeerInformation getPeerInformation() {
        return peerInformation;
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), peerInformation, correspondingId);
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
        final IdentityMessage that = (IdentityMessage) o;
        return Objects.equals(peerInformation, that.peerInformation) &&
                Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public String toString() {
        return "IdentityMessage{" +
                "sender=" + sender +
                ", peerInformation=" + peerInformation +
                ", correspondingId='" + correspondingId + '\'' +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                '}';
    }
}