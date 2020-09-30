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
    private final CompressedPublicKey publicKey;
    private final PeerInformation peerInformation;
    private final MessageId correspondingId;

    public IdentityMessage(CompressedPublicKey recipient,
                           CompressedPublicKey publicKey,
                           PeerInformation peerInformation,
                           MessageId correspondingId) {
        super(recipient);
        this.publicKey = requireNonNull(publicKey);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    @JsonCreator
    private IdentityMessage(@JsonProperty("id") MessageId id,
                            @JsonProperty("recipient") CompressedPublicKey recipient,
                            @JsonProperty("publicKey") CompressedPublicKey publicKey,
                            @JsonProperty("peerInformation") PeerInformation peerInformation,
                            @JsonProperty("correspondingId") MessageId correspondingId,
                            @JsonProperty("hopCount") short hopCount) {
        super(id, recipient, hopCount);
        this.publicKey = requireNonNull(publicKey);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
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
        return Objects.hash(super.hashCode(), publicKey, peerInformation, correspondingId);
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
        IdentityMessage that = (IdentityMessage) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(peerInformation, that.peerInformation) &&
                Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public String toString() {
        return "IdentityMessage{" +
                "identity=" + publicKey +
                ", peerInformation=" + peerInformation +
                ", correspondingId='" + correspondingId + '\'' +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                '}';
    }
}