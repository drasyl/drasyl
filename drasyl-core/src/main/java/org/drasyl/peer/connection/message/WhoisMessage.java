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
 * This message is used to request information (like public key and endpoints) for a specific
 * identity.
 * <p>
 * This is an immutable object.
 */
public class WhoisMessage extends RelayableMessage implements RequestMessage {
    private final PeerInformation peerInformation;

    @JsonCreator
    private WhoisMessage(@JsonProperty("id") final MessageId id,
                         @JsonProperty("hopCount") final short hopCount,
                         @JsonProperty("recipient") final CompressedPublicKey recipient,
                         @JsonProperty("sender") final CompressedPublicKey sender,
                         @JsonProperty("peerInformation") final PeerInformation peerInformation) {
        super(id, recipient, sender, hopCount);
        this.peerInformation = requireNonNull(peerInformation);
    }

    public WhoisMessage(final CompressedPublicKey recipient,
                        final CompressedPublicKey sender,
                        final PeerInformation peerInformation) {
        super(recipient, sender);
        this.peerInformation = requireNonNull(peerInformation);
    }

    public PeerInformation getPeerInformation() {
        return peerInformation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), peerInformation);
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
        final WhoisMessage that = (WhoisMessage) o;
        return Objects.equals(peerInformation, that.peerInformation);
    }

    @Override
    public String toString() {
        return "WhoisMessage{" +
                "sender=" + sender +
                ", peerInformation=" + peerInformation +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                '}';
    }
}