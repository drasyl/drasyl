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

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeerInformation;

import java.util.Objects;

/**
 * This message is used as a response to a {@link WhoisMessage} and contains information about a
 * peer (like public key and endpoints).
 */
public class IdentityMessage extends RelayableMessage implements ResponseMessage<WhoisMessage> {
    private final CompressedPublicKey publicKey;
    private final PeerInformation peerInformation;
    private final String correspondingId;

    private IdentityMessage() {
        super();
        publicKey = null;
        peerInformation = null;
        correspondingId = null;
    }

    public IdentityMessage(CompressedPublicKey recipient,
                           CompressedPublicKey publicKey,
                           PeerInformation peerInformation,
                           String correspondingId) {
        super(recipient);
        this.publicKey = publicKey;
        this.peerInformation = peerInformation;
        this.correspondingId = correspondingId;
    }

    public IdentityMessage(CompressedPublicKey recipient,
                           CompressedPublicKey publicKey,
                           PeerInformation peerInformation,
                           String correspondingId,
                           short hopCount) {
        super(recipient, hopCount);
        this.publicKey = publicKey;
        this.peerInformation = peerInformation;
        this.correspondingId = correspondingId;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public PeerInformation getPeerInformation() {
        return peerInformation;
    }

    @Override
    public String getCorrespondingId() {
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
