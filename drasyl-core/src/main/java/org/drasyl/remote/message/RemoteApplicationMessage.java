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
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.Protocol.Application;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.message.MessageId.randomMessageId;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;

@SuppressWarnings({ "java:S107" })
public class RemoteApplicationMessage extends AbstractMessage {
    protected final String type;
    protected final byte[] payload;

    protected RemoteApplicationMessage(final MessageId id,
                                       final UserAgent userAgent,
                                       final int networkId,
                                       final CompressedPublicKey sender,
                                       final ProofOfWork proofOfWork,
                                       final CompressedPublicKey recipient,
                                       final byte hopCount,
                                       final Signature signature,
                                       final String type,
                                       final byte[] payload) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount, signature);
        this.type = Objects.requireNonNullElseGet(type, byte[].class::getName);
        this.payload = requireNonNull(payload);
    }

    /**
     * Creates a new message.
     *
     * @param networkId   the network the sender belongs to
     * @param sender      the sender
     * @param proofOfWork the sender's proof of work
     * @param recipient   the recipient
     * @param payload     the data to be sent
     */
    public RemoteApplicationMessage(final int networkId,
                                    final CompressedPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final CompressedPublicKey recipient,
                                    final String type,
                                    final byte[] payload) {
        this(networkId, sender, proofOfWork, recipient, type, payload, (byte) 0, null);
    }

    public RemoteApplicationMessage(final int networkId,
                                    final CompressedPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final CompressedPublicKey recipient,
                                    final String type,
                                    final byte[] payload,
                                    final byte hopCount,
                                    final Signature signature) {
        this(randomMessageId(), UserAgent.generate(), networkId, sender, proofOfWork, recipient, hopCount, signature, type, payload);
    }

    /**
     * Creates a new message.
     *
     * @param networkId   the network the sender belongs to
     * @param sender      the sender
     * @param proofOfWork the sender's proof of work
     * @param recipient   the recipient
     * @param payload     the data to be sent
     */
    public RemoteApplicationMessage(final int networkId,
                                    final CompressedPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final CompressedPublicKey recipient,
                                    final byte[] payload) {
        this(networkId, sender, proofOfWork, recipient, byte[].class.getName(), payload, (byte) 0, null);
    }

    /**
     * Creates a new message.
     *
     * @param header the public header
     * @param body   the remote application message body
     */
    public RemoteApplicationMessage(final Protocol.PublicHeader header,
                                    final Application body) throws Exception {
        super(header);
        this.payload = requireNonNull(body.getPayload().toByteArray());
        this.type = requireNonNull(body.getType());
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(Objects.hash(networkId, sender, proofOfWork, recipient, hopCount, signature), type);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        final boolean result;
        if (this == o) {
            result = true;
        }
        else if (o == null || getClass() != o.getClass()) {
            result = false;
        }
        else {
            final RemoteApplicationMessage that1 = (RemoteApplicationMessage) o;
            result = networkId == that1.networkId &&
                    Objects.equals(sender, that1.sender) &&
                    Objects.equals(proofOfWork, that1.proofOfWork) &&
                    Objects.equals(recipient, that1.recipient) &&
                    Objects.equals(signature, that1.signature);
        }
        if (!result) {
            return false;
        }
        final RemoteApplicationMessage that = (RemoteApplicationMessage) o;
        return Arrays.equals(payload, that.payload) &&
                Objects.equals(type, that.type);
    }

    @Override
    public String toString() {
        return "RemoteApplicationMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", signature=" + signature +
                ", type=" + type +
                ", payload=byte[" + payload.length + "] { ... }" +
                ", id='" + id + '\'' +
                '}';
    }

    public String getType() {
        return type;
    }

    @Override
    public Protocol.PrivateHeader getPrivateHeader() {
        return Protocol.PrivateHeader.newBuilder()
                .setType(APPLICATION)
                .build();
    }

    @Override
    public Application getBody() {
        return Application.newBuilder()
                .setType(type)
                .setPayload(ByteString.copyFrom(payload))
                .build();
    }
}
