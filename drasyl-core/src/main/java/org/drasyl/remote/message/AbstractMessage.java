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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.message.MessageId.randomMessageId;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Message that represents a message from one node to another one.
 */
@SuppressWarnings({ "squid:S1444", "squid:ClassVariableVisibilityCheck" })
abstract class AbstractMessage implements RemoteMessage {
    public static final Supplier<String> defaultUserAgentGenerator = () -> "drasyl/" + DrasylNode.getVersion() + " (" + System.getProperty("os.name") + "; "
            + System.getProperty("os.arch") + "; Java/"
            + System.getProperty("java.vm.specification.version") + ":" + System.getProperty("java.version.date")
            + ")";
    public static Supplier<String> userAgentGenerator = defaultUserAgentGenerator;
    protected final MessageId id;
    protected final UserAgent userAgent;
    protected final int networkId;
    protected final CompressedPublicKey sender;
    protected final ProofOfWork proofOfWork;
    protected final CompressedPublicKey recipient;
    protected short hopCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
                              final short hopCount,
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
                              final short hopCount,
                              final Signature signature) {
        this(randomMessageId(), UserAgent.generate(), networkId, sender, proofOfWork, recipient, hopCount, signature);
    }

    protected AbstractMessage(final int networkId,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient) {
        this(networkId, sender, proofOfWork, recipient, (short) 0, null);
    }

    protected AbstractMessage(final MessageId id,
                              final int networkId,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final short hopCount,
                              final Signature signature) {
        this(id, UserAgent.generate(), networkId, sender, proofOfWork, recipient, hopCount, signature);
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
        JACKSON_WRITER.writeValue(outstream, this);
        this.signature = tempSignature;
    }
}