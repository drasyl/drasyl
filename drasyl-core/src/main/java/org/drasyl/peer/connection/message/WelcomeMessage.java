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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeerInformation;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing the welcome message of the node server, including fallback information.
 * <p>
 * This is an immutable object.
 */
public class WelcomeMessage extends AbstractMessageWithUserAgent implements ResponseMessage<JoinMessage> {
    private final int networkId;
    private final CompressedPublicKey publicKey;
    private final ProofOfWork proofOfWork;
    private final PeerInformation peerInformation;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final MessageId correspondingId;

    @JsonCreator
    private WelcomeMessage(@JsonProperty("id") final MessageId id,
                           @JsonProperty("userAgent") final String userAgent,
                           @JsonProperty("networkId") final int networkId,
                           @JsonProperty("publicKey") final CompressedPublicKey publicKey,
                           @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                           @JsonProperty("peerInformation") final PeerInformation peerInformation,
                           @JsonProperty("correspondingId") final MessageId correspondingId) {
        super(id, userAgent);
        this.networkId = networkId;
        this.publicKey = requireNonNull(publicKey);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    /**
     * Creates new welcome message.
     *
     * @param networkId       the network id of the node server
     * @param publicKey       the public key of the node server
     * @param proofOfWork     the proof of work of the node server
     * @param peerInformation the peer information of the node server
     * @param correspondingId the corresponding id of the previous join message
     */
    public WelcomeMessage(final int networkId,
                          final CompressedPublicKey publicKey,
                          final ProofOfWork proofOfWork,
                          final PeerInformation peerInformation,
                          final MessageId correspondingId) {
        this.networkId = networkId;
        this.publicKey = requireNonNull(publicKey);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.peerInformation = requireNonNull(peerInformation);
        this.correspondingId = requireNonNull(correspondingId);
    }

    public int getNetworkId() {
        return networkId;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    public PeerInformation getPeerInformation() {
        return this.peerInformation;
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), networkId, publicKey, proofOfWork, peerInformation, correspondingId);
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
        final WelcomeMessage that = (WelcomeMessage) o;
        return networkId == that.networkId &&
                Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(peerInformation, that.peerInformation) &&
                Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public String toString() {
        return "WelcomeMessage{" +
                "networkId=" + networkId +
                ", publicKey=" + publicKey +
                ", proofOfWork=" + proofOfWork +
                ", peerInformation=" + peerInformation +
                ", correspondingId=" + correspondingId +
                ", id=" + id +
                '}';
    }
}