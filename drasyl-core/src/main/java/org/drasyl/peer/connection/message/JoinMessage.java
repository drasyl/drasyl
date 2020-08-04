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
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a join to the node server.
 */
public class JoinMessage extends AbstractMessageWithUserAgent implements RequestMessage {
    private final ProofOfWork proofOfWork;
    private final CompressedPublicKey publicKey;
    private final boolean childrenJoin;
    private final Set<CompressedPublicKey> childrenAndGrandchildren;

    @JsonCreator
    private JoinMessage(@JsonProperty("id") MessageId id,
                        @JsonProperty("userAgent") String userAgent,
                        @JsonProperty("proofOfWork") ProofOfWork proofOfWork,
                        @JsonProperty("publicKey") CompressedPublicKey publicKey,
                        @JsonProperty("childrenJoin") boolean childrenJoin,
                        @JsonProperty("childrenAndGrandchildren") Set<CompressedPublicKey> childrenAndGrandchildren) {
        super(id, userAgent);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.publicKey = requireNonNull(publicKey);
        this.childrenJoin = childrenJoin;
        this.childrenAndGrandchildren = requireNonNull(childrenAndGrandchildren);
    }

    /**
     * Creates a new join message.
     *
     * @param proofOfWork              the proof of work
     * @param publicKey                the identity of the joining node
     * @param childrenAndGrandchildren the (grand-)children of this node
     */
    public JoinMessage(ProofOfWork proofOfWork,
                       CompressedPublicKey publicKey,
                       Set<CompressedPublicKey> childrenAndGrandchildren) {
        this(proofOfWork, publicKey, true, Set.copyOf(childrenAndGrandchildren));
    }

    /**
     * Creates a new join message.
     *
     * @param proofOfWork              the proof of work
     * @param publicKey                the identity of the joining node
     * @param childrenJoin             join peer as children
     * @param childrenAndGrandchildren the (grand-)children of this node
     */
    public JoinMessage(ProofOfWork proofOfWork,
                       CompressedPublicKey publicKey,
                       boolean childrenJoin,
                       Set<CompressedPublicKey> childrenAndGrandchildren) {
        this.proofOfWork = requireNonNull(proofOfWork);
        this.publicKey = requireNonNull(publicKey);
        this.childrenJoin = childrenJoin;
        this.childrenAndGrandchildren = Set.copyOf(childrenAndGrandchildren);
    }

    public boolean isChildrenJoin() {
        return childrenJoin;
    }

    public Set<CompressedPublicKey> getChildrenAndGrandchildren() {
        return this.childrenAndGrandchildren;
    }

    public CompressedPublicKey getPublicKey() {
        return this.publicKey;
    }

    public ProofOfWork getProofOfWork() {
        return this.proofOfWork;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), proofOfWork, publicKey, childrenJoin, childrenAndGrandchildren);
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
                Objects.equals(childrenAndGrandchildren, that.childrenAndGrandchildren);
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "proofOfWork=" + proofOfWork +
                ", publicKey=" + publicKey +
                ", childrenJoin=" + childrenJoin +
                ", childrenAndGrandchildren=" + childrenAndGrandchildren +
                ", id='" + id + '\'' +
                '}';
    }
}