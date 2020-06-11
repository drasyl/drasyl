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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.drasyl.crypto.Signable;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents a container with a signature for the {@link #payload}. The {@link #signature} must be
 * valid under the supplied {@link #kid public key}.
 * <br>
 * <b>
 * The {@link #signature} does not give any guarantees about the logical integrity of the {@link
 * #payload}, but only guarantees that the {@link #payload} was sent by the identity with the {@link
 * #kid public key} (as the last node in a relay chain).
 * </b>
 */
public class SignedMessage implements Message, Signable {
    private static final ObjectMapper JACKSON = new ObjectMapper();
    private final Message payload;
    private final CompressedPublicKey kid;
    private Signature signature;

    private SignedMessage() {
        this.payload = null;
        this.signature = null;
        this.kid = null;
    }

    public SignedMessage(Message payload, CompressedPublicKey publicKey) {
        requireNonNull(payload);
        this.payload = payload;
        this.kid = publicKey;
    }

    public Message getPayload() {
        return this.payload;
    }

    public CompressedPublicKey getKid() {
        return this.kid;
    }

    @Override
    public void writeFieldsTo(OutputStream outstream) throws IOException {
        requireNonNull(payload);

        outstream.write(JACKSON.writeValueAsBytes(payload));
    }

    @Override
    public Signature getSignature() {
        return this.signature;
    }

    @Override
    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    @Override
    @JsonIgnore
    public String getId() {
        return "SignedMessage";
    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, kid, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SignedMessage that = (SignedMessage) o;
        return Objects.equals(payload, that.payload) &&
                Objects.equals(kid, that.kid) &&
                Objects.equals(signature, that.signature);
    }
}
