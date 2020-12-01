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
package org.drasyl.remote.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.net.InetSocketAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This message is sent to another node to inform them about the presence of a third node.
 */
public class UniteMessage extends AbstractMessage {
    private final CompressedPublicKey publicKey;
    private final InetSocketAddress address;

    @JsonCreator
    private UniteMessage(@JsonProperty("id") final MessageId id,
                         @JsonProperty("userAgent") final UserAgent userAgent,
                         @JsonProperty("networkId") final int networkId,
                         @JsonProperty("sender") final CompressedPublicKey sender,
                         @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                         @JsonProperty("recipient") final CompressedPublicKey recipient,
                         @JsonProperty("hopCount") final short hopCount,
                         @JsonProperty("signature") final Signature signature,
                         @JsonProperty("publicKey") final CompressedPublicKey publicKey,
                         @JsonProperty("address") final InetSocketAddress address) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount, signature);
        this.publicKey = requireNonNull(publicKey);
        this.address = requireNonNull(address);
    }

    public UniteMessage(final int networkId,
                        final CompressedPublicKey sender,
                        final ProofOfWork proofOfWork,
                        final CompressedPublicKey recipient,
                        final CompressedPublicKey publicKey,
                        final InetSocketAddress address) {
        super(networkId, sender, proofOfWork, recipient);
        this.publicKey = requireNonNull(publicKey);
        this.address = requireNonNull(address);
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public InetSocketAddress getAddress() {
        return address;
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
        final UniteMessage that = (UniteMessage) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, address);
    }

    @Override
    public String toString() {
        return "UniteMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", signature=" + signature +
                ", publicKey=" + publicKey +
                ", address=" + address +
                ", id=" + id +
                '}';
    }
}
