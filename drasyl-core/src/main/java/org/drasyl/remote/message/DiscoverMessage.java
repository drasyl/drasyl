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

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;

import java.util.Objects;

import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;

/**
 * This message is sent to other peers to inform them about the existence of this node.
 */
public class DiscoverMessage extends AbstractMessage {
    private final long joinTime;

    /**
     * Creates a new join message.
     *
     * @param networkId   the network of the joining node
     * @param sender      the public key of the joining node
     * @param proofOfWork the proof of work
     * @param recipient   the public key of the node to join
     * @param joinTime    if {@code 0} greater then 0, node will join a children.
     */
    public DiscoverMessage(final int networkId,
                           final CompressedPublicKey sender,
                           final ProofOfWork proofOfWork,
                           final CompressedPublicKey recipient,
                           final long joinTime) {
        super(networkId, sender, proofOfWork, recipient);
        this.joinTime = joinTime;
    }

    public DiscoverMessage(final Protocol.PublicHeader header,
                           final Discovery body) throws Exception {
        super(header);
        this.joinTime = body.getChildrenTime();
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
        final DiscoverMessage that = (DiscoverMessage) o;
        return joinTime == that.joinTime;
    }

    @Override
    public String toString() {
        return "DiscoverMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", signature=" + signature +
                ", joinTime=" + joinTime +
                ", id=" + id +
                '}';
    }

    @Override
    public PrivateHeader getPrivateHeader() {
        return PrivateHeader.newBuilder()
                .setType(DISCOVERY)
                .build();
    }

    @Override
    public Discovery getBody() {
        return Discovery.newBuilder()
                .setChildrenTime(joinTime)
                .build();
    }
}