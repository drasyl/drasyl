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
import com.google.protobuf.MessageLite;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.PublicHeader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.message.MessageId.randomMessageId;

/**
 * Message that represents a message from one node to another one.
 */
@SuppressWarnings({ "squid:S1444", "squid:ClassVariableVisibilityCheck", "java:S107" })
abstract class AbstractMessage<T extends MessageLite> implements RemoteMessage<T> {
    protected final MessageId id;
    protected final UserAgent userAgent;
    protected final int networkId;
    protected final CompressedPublicKey sender;
    protected final ProofOfWork proofOfWork;
    protected final CompressedPublicKey recipient;
    protected byte hopCount;
    protected Signature signature;

    /**
     * @param id          message's identifier
     * @param userAgent   message's user agent
     * @param networkId   message's network
     * @param sender      message's sender
     * @param proofOfWork sender's proof of work
     * @param recipient   message's recipient
     * @param hopCount    message's hop count
     * @param signature   message's signature
     * @throws IllegalArgumentException if hopCount is negative
     */
    protected AbstractMessage(final MessageId id,
                              final UserAgent userAgent,
                              final int networkId,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final byte hopCount,
                              final Signature signature) {
        this.id = requireNonNull(id);
        this.userAgent = requireNonNull(userAgent);
        this.networkId = networkId;
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.recipient = requireNonNull(recipient);
        if (hopCount < 0) {
            throw new IllegalArgumentException("hopCount must not be negative.");
        }
        this.hopCount = hopCount;
        this.signature = signature;
    }

    protected AbstractMessage(final int networkId,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final byte hopCount,
                              final Signature signature) {
        this(randomMessageId(), UserAgent.generate(), networkId, sender, proofOfWork, recipient, hopCount, signature);
    }

    protected AbstractMessage(final int networkId,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient) {
        this(networkId, sender, proofOfWork, recipient, (byte) 0, null);
    }

    protected AbstractMessage(final MessageId id,
                              final int networkId,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final byte hopCount,
                              final Signature signature) {
        this(id, UserAgent.generate(), networkId, sender, proofOfWork, recipient, hopCount, signature);
    }

    protected AbstractMessage(final PublicHeader header) throws Exception {
        this(MessageId.of(header.getId().toByteArray()), new UserAgent(header.getUserAgent().toByteArray()),
                header.getNetworkId(), CompressedPublicKey.of(header.getSender().toByteArray()),
                ProofOfWork.of(header.getProofOfWork()),
                CompressedPublicKey.of(header.getRecipient().toByteArray()),
                header.getHopCount().byteAt(0),
                null);

        final byte[] sig = header.getSignature().toByteArray();
        if (sig != null && sig.length != 0) {
            setSignature(new Signature(sig));
        }
    }

    @Override
    public MessageId getId() {
        return id;
    }

    @Override
    public UserAgent getUserAgent() {
        return userAgent;
    }

    @Override
    public int getNetworkId() {
        return networkId;
    }

    @Override
    public CompressedPublicKey getSender() {
        return sender;
    }

    @Override
    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public CompressedPublicKey getRecipient() {
        return recipient;
    }

    @Override
    public byte getHopCount() {
        return hopCount;
    }

    @Override
    public void incrementHopCount() {
        hopCount++;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public void setSignature(final Signature signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractMessage that = (AbstractMessage) o;
        return networkId == that.networkId &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(recipient, that.recipient) &&
                Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkId, sender, proofOfWork, recipient, hopCount, signature);
    }

    @Override
    public void writeFieldsTo(final OutputStream outstream) throws IOException {
        final Signature tempSignature = this.signature;
        this.signature = null;
        getPublicHeader().writeDelimitedTo(outstream);
        getPrivateHeader().writeDelimitedTo(outstream);
        getBody().writeDelimitedTo(outstream);
        this.signature = tempSignature;
    }

    @Override
    public PublicHeader getPublicHeader() {
        final PublicHeader.Builder builder = PublicHeader.newBuilder()
                .setId(ByteString.copyFrom(id.byteArrayValue()))
                .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                .setNetworkId(networkId)
                .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                .setProofOfWork(proofOfWork.intValue())
                .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                .setHopCount(ByteString.copyFrom(new byte[]{ hopCount }));

        if (signature != null && signature.getBytes() != null) {
            builder.setSignature(ByteString.copyFrom(signature.getBytes()));
        }

        return builder.build();
    }
}