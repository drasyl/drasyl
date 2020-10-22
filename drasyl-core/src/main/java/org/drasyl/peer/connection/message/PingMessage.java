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

import static java.util.Objects.requireNonNull;

/**
 * A message representing a PING request.
 * <p>
 * This is an immutable object.
 */
public class PingMessage extends AbstractMessage implements RequestMessage {
    private final CompressedPublicKey sender;
    private final ProofOfWork proofOfWork;

    @JsonCreator
    private PingMessage(@JsonProperty("id") final MessageId id,
                        @JsonProperty("sender") final CompressedPublicKey sender,
                        @JsonProperty("proofOfWork") final ProofOfWork proofOfWork) {
        super(id);
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
    }

    public PingMessage(final CompressedPublicKey sender,
                       final ProofOfWork proofOfWork) {
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
    }

    public CompressedPublicKey getSender() {
        return sender;
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public String toString() {
        return "PingMessage{" +
                "sender='" + sender +
                ", proofOfWork='" + proofOfWork +
                ", id='" + id +
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
        final PingMessage that = (PingMessage) o;
        return Objects.equals(sender, that.sender) &&
                Objects.equals(proofOfWork, that.proofOfWork);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sender, proofOfWork);
    }
}