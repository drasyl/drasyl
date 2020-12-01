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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.message.MessageId.randomMessageId;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

public class RemoteApplicationMessage implements RemoteMessage {
    protected final String type;
    protected final byte[] payload;
    protected final MessageId id;
    protected final UserAgent userAgent;
    protected final int networkId;
    protected final CompressedPublicKey sender;
    protected final ProofOfWork proofOfWork;
    protected final CompressedPublicKey recipient;
    protected short hopCount;
    protected Signature signature;

    @JsonCreator
    private RemoteApplicationMessage(@JsonProperty("id") final MessageId id,
                                     @JsonProperty("userAgent") final UserAgent userAgent,
                                     @JsonProperty("networkId") final int networkId,
                                     @JsonProperty("sender") final CompressedPublicKey sender,
                                     @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                                     @JsonProperty("recipient") final CompressedPublicKey recipient,
                                     @JsonProperty("hopCount") final short hopCount,
                                     @JsonProperty("signature") final Signature signature,
                                     @JsonProperty("type") final String type,
                                     @JsonProperty("payload") final byte[] payload) {
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
        this.type = Objects.requireNonNullElseGet(type, byte[].class::getName);
        this.payload = requireNonNull(payload);
    }

    @SuppressWarnings({ "java:S107" })
    public RemoteApplicationMessage(final MessageId id,
                                    final UserAgent userAgent,
                                    final int networkId,
                                    final CompressedPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final CompressedPublicKey recipient,
                                    final short hopCount,
                                    final Signature signature,
                                    final byte[] payload) {
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
        this.type = byte[].class.getName();
        this.payload = payload;
    }

    public RemoteApplicationMessage(final MessageId id,
                                    final int networkId,
                                    final CompressedPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final CompressedPublicKey recipient,
                                    final short hopCount,
                                    final Signature signature,
                                    final byte[] payload) {
        this(id, UserAgent.generate(), networkId, sender, proofOfWork, recipient, hopCount, signature, payload);
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
        this(networkId, sender, proofOfWork, recipient, type, payload, (short) 0, null);
    }

    public RemoteApplicationMessage(final int networkId,
                                    final CompressedPublicKey sender,
                                    final ProofOfWork proofOfWork,
                                    final CompressedPublicKey recipient,
                                    final String type,
                                    final byte[] payload,
                                    final short hopCount,
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
        this(networkId, sender, proofOfWork, recipient, byte[].class.getName(), payload, (short) 0, null);
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
    public short getHopCount() {
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
    public void writeFieldsTo(final OutputStream outstream) throws IOException {
        final Signature tempSignature = this.signature;
        this.signature = null;
        JACKSON_WRITER.writeValue(outstream, this);
        this.signature = tempSignature;
    }

    public String getType() {
        return type;
    }
}
