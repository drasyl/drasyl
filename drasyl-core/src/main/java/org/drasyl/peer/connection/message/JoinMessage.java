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

/**
 * A message representing a join to the node server.
 */
public class JoinMessage extends AbstractMessage implements RequestMessage {
    private final long joinTime;

    @JsonCreator
    private JoinMessage(@JsonProperty("id") final MessageId id,
                        @JsonProperty("userAgent") final UserAgent userAgent,
                        @JsonProperty("networkId") final int networkId,
                        @JsonProperty("sender") final CompressedPublicKey sender,
                        @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                        @JsonProperty("recipient") final CompressedPublicKey recipient,
                        @JsonProperty("hopCount") final short hopCount,
                        @JsonProperty("childrenTime") final long joinTime) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount);
        this.joinTime = joinTime;
    }

    /**
     * Creates a new join message.
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @param recipient   the public key of the node to join
     * @param joinTime    if {@code 0} greater then 0, node will join a children.
     */
    public JoinMessage(final int networkId,
                       final CompressedPublicKey sender,
                       final ProofOfWork proofOfWork,
                       final CompressedPublicKey recipient,
                       final long joinTime) {
        super(networkId, sender, proofOfWork, recipient);
        this.joinTime = joinTime;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public boolean isChildrenJoin() {
        return joinTime > 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), joinTime);
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
        return joinTime == that.joinTime;
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", joinTime=" + joinTime +
                ", id=" + id +
                '}';
    }
}