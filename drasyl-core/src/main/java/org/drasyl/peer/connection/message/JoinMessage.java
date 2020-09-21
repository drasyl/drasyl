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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a join to the node server.
 */
public class JoinMessage extends AbstractMessageWithUserAgent implements RequestMessage {
    private final ProofOfWork proofOfWork;
    private final CompressedPublicKey publicKey;
    private final boolean childrenJoin;
    private final int networkId;

    @JsonCreator
    private JoinMessage(@JsonProperty("id") MessageId id,
                        @JsonProperty("userAgent") String userAgent,
                        @JsonProperty("proofOfWork") ProofOfWork proofOfWork,
                        @JsonProperty("publicKey") CompressedPublicKey publicKey,
                        @JsonProperty("childrenJoin") boolean childrenJoin,
                        @JsonProperty("networkId") int networkId) {
        super(id, userAgent);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.publicKey = requireNonNull(publicKey);
        this.childrenJoin = childrenJoin;
        this.networkId = networkId;
    }

    /**
     * Creates a new join message.
     *
     * @param proofOfWork the proof of work
     * @param publicKey   the identity of the joining node
     * @param networkId   the network of the joining node
     */
    public JoinMessage(ProofOfWork proofOfWork,
                       CompressedPublicKey publicKey,
                       int networkId) {
        this(proofOfWork, publicKey, true, networkId);
    }

    /**
     * Creates a new join message.
     *
     * @param proofOfWork  the proof of work
     * @param publicKey    the identity of the joining node
     * @param childrenJoin join peer as children
     * @param networkId    the network of the joining node
     */
    public JoinMessage(ProofOfWork proofOfWork,
                       CompressedPublicKey publicKey,
                       boolean childrenJoin,
                       int networkId) {
        this.proofOfWork = requireNonNull(proofOfWork);
        this.publicKey = requireNonNull(publicKey);
        this.childrenJoin = childrenJoin;
        this.networkId = networkId;
    }

    public boolean isChildrenJoin() {
        return childrenJoin;
    }

    public CompressedPublicKey getPublicKey() {
        return this.publicKey;
    }

    public ProofOfWork getProofOfWork() {
        return this.proofOfWork;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), proofOfWork, publicKey, childrenJoin, networkId);
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
        JoinMessage that = (JoinMessage) o;
        return childrenJoin == that.childrenJoin &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(publicKey, that.publicKey) &&
                networkId == that.networkId;
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "proofOfWork=" + proofOfWork +
                ", publicKey=" + publicKey +
                ", childrenJoin=" + childrenJoin +
                ", networkId=" + networkId +
                ", id=" + id +
                '}';
    }
}