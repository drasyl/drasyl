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
 * <p>
 * This is an immutable object.
 */
public class JoinMessage extends AbstractMessageWithUserAgent implements RequestMessage, AddressableMessage {
    private final int networkId;
    private final ProofOfWork proofOfWork;
    private final CompressedPublicKey sender;
    private final CompressedPublicKey recipient;
    private final boolean childrenJoin;

    @JsonCreator
    private JoinMessage(@JsonProperty("id") final MessageId id,
                        @JsonProperty("userAgent") final String userAgent,
                        @JsonProperty("networkId") final int networkId,
                        @JsonProperty("sender") final CompressedPublicKey sender,
                        @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                        @JsonProperty("recipient") final CompressedPublicKey recipient,
                        @JsonProperty("childrenJoin") final boolean childrenJoin) {
        super(id, userAgent);
        this.networkId = networkId;
        this.proofOfWork = requireNonNull(proofOfWork);
        this.sender = requireNonNull(sender);
        this.recipient = recipient;
        this.childrenJoin = childrenJoin;
    }

    /**
     * Creates a new join message.
     *
     * @param networkId    the network of the joining node
     * @param sender       the public key of the joining node
     * @param proofOfWork  the proof of work
     * @param recipient    the public key of the node to join
     * @param childrenJoin join peer as children
     */
    public JoinMessage(final int networkId,
                       final CompressedPublicKey sender,
                       final ProofOfWork proofOfWork,
                       final CompressedPublicKey recipient,
                       final boolean childrenJoin) {
        this.proofOfWork = requireNonNull(proofOfWork);
        this.sender = requireNonNull(sender);
        this.recipient = requireNonNull(recipient);
        this.childrenJoin = childrenJoin;
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public CompressedPublicKey getSender() {
        return this.sender;
    }

    public ProofOfWork getProofOfWork() {
        return this.proofOfWork;
    }

    public CompressedPublicKey getRecipient() {
        return recipient;
    }

    public boolean isChildrenJoin() {
        return childrenJoin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), networkId, sender, proofOfWork, recipient, childrenJoin);
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
        final JoinMessage that = (JoinMessage) o;
        return networkId == that.networkId &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(recipient, that.recipient) &&
                childrenJoin == that.childrenJoin;
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", childrenJoin=" + childrenJoin +
                ", id=" + id +
                '}';
    }
}