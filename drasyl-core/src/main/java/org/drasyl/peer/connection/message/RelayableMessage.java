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

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.util.Objects;

/**
 * Includes messages that can be relayed to their recipient via multiple hops.
 */
public abstract class RelayableMessage extends AbstractMessage {
    protected short hopCount;

    protected RelayableMessage(final MessageId id,
                               final String userAgent,
                               final int networkId,
                               final CompressedPublicKey sender,
                               final ProofOfWork proofOfWork,
                               final CompressedPublicKey recipient,
                               final short hopCount) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient);
        this.hopCount = hopCount;
    }

    protected RelayableMessage(final MessageId id,
                               final int networkId,
                               final CompressedPublicKey sender,
                               final ProofOfWork proofOfWork,
                               final CompressedPublicKey recipient,
                               final short hopCount) {
        super(id, userAgentGenerator.get(), networkId, sender, proofOfWork, recipient);
        this.hopCount = hopCount;
    }

    protected RelayableMessage(final int networkId,
                               final CompressedPublicKey sender,
                               final ProofOfWork proofOfWork,
                               final CompressedPublicKey recipient) {
        this(networkId, sender, proofOfWork, recipient, (short) 0);
    }

    protected RelayableMessage(final int networkId,
                               final CompressedPublicKey sender,
                               final ProofOfWork proofOfWork,
                               final CompressedPublicKey recipient,
                               final short hopCount) {
        super(networkId, sender, proofOfWork, recipient);
        this.hopCount = hopCount;
    }

    public short getHopCount() {
        return hopCount;
    }

    /**
     * Increments the hop count value of this message.
     */
    public void incrementHopCount() {
        hopCount++;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), hopCount);
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
        final RelayableMessage that = (RelayableMessage) o;
        return hopCount == that.hopCount;
    }
}