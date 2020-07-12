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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Includes messages that can be relayed to their recipient via multiple hops.
 */
public abstract class RelayableMessage extends AbstractMessage {
    protected final CompressedPublicKey recipient;
    protected short hopCount;

    protected RelayableMessage(String id,
                               CompressedPublicKey recipient,
                               short hopCount) {
        super(id);
        this.recipient = requireNonNull(recipient);
        this.hopCount = hopCount;
    }

    protected RelayableMessage(CompressedPublicKey recipient) {
        this(recipient, (short) 0);
    }

    protected RelayableMessage(CompressedPublicKey recipient, short hopCount) {
        super();
        this.recipient = requireNonNull(recipient);
        this.hopCount = hopCount;
    }

    protected RelayableMessage() {
        super();
        recipient = null;
        hopCount = (short) 0;
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

    public CompressedPublicKey getRecipient() {
        return recipient;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), recipient, hopCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        RelayableMessage that = (RelayableMessage) o;
        return hopCount == that.hopCount &&
                Objects.equals(recipient, that.recipient);
    }
}
