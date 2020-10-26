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
 */
public class SignedMessage extends AbstractMessage implements Signable {
    private Signature signature;
    private final Message payload;

    @JsonCreator
    SignedMessage(@JsonProperty("id") final MessageId id,
                  @JsonProperty("userAgent") final String userAgent,
                  @JsonProperty("networkId") final int networkId,
                  @JsonProperty("sender") final CompressedPublicKey sender,
                  @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                  @JsonProperty("recipient") final CompressedPublicKey recipient,
                  @JsonProperty("hopCount") final short hopCount,
                  @JsonProperty("signature") final Signature signature,
                  @JsonProperty("payload") final Message payload) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount);
        this.signature = signature;
        this.payload = requireNonNull(payload);
    }

    public SignedMessage(final int networkId,
                         final CompressedPublicKey sender,
                         final ProofOfWork proofOfWork,
                         final CompressedPublicKey recipient,
                         final Message payload) {
        super(networkId, sender, proofOfWork, recipient);
        this.payload = requireNonNull(payload);
    }

    public Message getPayload() {
        return this.payload;
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
    public String toString() {
        return "SignedMessage{" +
                "networkId=" + networkId +
                ", sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", signature=" + signature +
                ", payload=" + payload +
                '}';
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
        final SignedMessage that = (SignedMessage) o;
        return Objects.equals(signature, that.signature) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), signature, payload);
    }
}