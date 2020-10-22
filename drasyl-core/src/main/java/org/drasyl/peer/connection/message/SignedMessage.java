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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.crypto.Signable;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

/**
 * Represents a container with a signature for the {@link #payload}. The {@link #signature} must be
 * valid under the supplied {@link #sender public key}.
 * <br>
 * <b>
 * The {@link #signature} does not give any guarantees about the logical integrity of the {@link
 * #payload}, but only guarantees that the {@link #payload} was sent by the identity with the {@link
 * #sender public key} (as the last node in a relay chain).
 * </b>
 * <p>
 * This is an immutable object.
 */
public class SignedMessage implements Message, Signable {
    private final CompressedPublicKey sender;
    private final ProofOfWork proofOfWork;
    private Signature signature;
    private final Message payload;

    public SignedMessage(final Message payload,
                         final CompressedPublicKey sender,
                         final ProofOfWork proofOfWork) {
        this(sender, proofOfWork, null, payload);
    }

    @JsonCreator
    SignedMessage(@JsonProperty("sender") final CompressedPublicKey sender,
                  @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                  @JsonProperty("signature") final Signature signature,
                  @JsonProperty("payload") final Message payload) {
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.signature = signature;
        this.payload = requireNonNull(payload);
    }

    public Message getPayload() {
        return this.payload;
    }

    public CompressedPublicKey getSender() {
        return this.sender;
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public void writeFieldsTo(final OutputStream outstream) throws IOException {
        requireNonNull(payload);

        JACKSON_WRITER.writeValue(outstream, payload);
    }

    @Override
    public Signature getSignature() {
        return this.signature;
    }

    @Override
    public void setSignature(final Signature signature) {
        this.signature = signature;
    }

    @Override
    @JsonIgnore
    public MessageId getId() {
        return MessageId.of("SignedMessage");
    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, sender, signature);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SignedMessage that = (SignedMessage) o;
        return Objects.equals(payload, that.payload) &&
                Objects.equals(sender, that.sender) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(signature, that.signature);
    }

    @Override
    public String toString() {
        return "SignedMessage{" +
                "payload=" + payload +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", signature=" + signature +
                '}';
    }
}