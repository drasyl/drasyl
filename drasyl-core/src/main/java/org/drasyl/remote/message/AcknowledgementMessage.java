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

import com.google.protobuf.ByteString;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;

/**
 * This message acts as an acknowledgement to a previously sent {@link DiscoverMessage}.
 */
public class AcknowledgementMessage extends AbstractMessage implements ResponseMessage<DiscoverMessage> {
    protected final MessageId correspondingId;

    /**
     * Creates new welcome message.
     *
     * @param networkId       the network id of the node server
     * @param sender          the public key of the node server
     * @param proofOfWork     the proof of work of the node server
     * @param recipient       the public key of the recipient
     * @param correspondingId the corresponding id of the previous join message
     */
    public AcknowledgementMessage(final int networkId,
                                  final CompressedPublicKey sender,
                                  final ProofOfWork proofOfWork,
                                  final CompressedPublicKey recipient,
                                  final MessageId correspondingId) {
        super(networkId, sender, proofOfWork, recipient);
        this.correspondingId = requireNonNull(correspondingId);
    }

    /**
     * Creates new welcome message.
     *
     * @param header          the public header
     * @param acknowledgement the acknowledgement message body
     * @throws CryptoException if the public header is not well-formed
     */
    public AcknowledgementMessage(final Protocol.PublicHeader header,
                                  final Acknowledgement acknowledgement) throws Exception {
        super(header);
        this.correspondingId = requireNonNull(MessageId.of(acknowledgement.getCorrespondingId().toByteArray()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), correspondingId);
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
        final AcknowledgementMessage that = (AcknowledgementMessage) o;
        return Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public String toString() {
        return "AcknowledgementMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", signature=" + signature +
                ", id=" + id +
                '}';
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public PrivateHeader getPrivateHeader() {
        return PrivateHeader.newBuilder()
                .setType(ACKNOWLEDGEMENT)
                .build();
    }

    @Override
    public Acknowledgement getBody() {
        return Acknowledgement.newBuilder()
                .setCorrespondingId(ByteString.copyFrom(correspondingId.getId()))
                .build();
    }
}