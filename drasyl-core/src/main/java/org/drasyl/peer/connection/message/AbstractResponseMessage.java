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

import static java.util.Objects.requireNonNull;

abstract class AbstractResponseMessage<R extends RequestMessage> extends AbstractMessage implements ResponseMessage<R> {
    protected final MessageId correspondingId;

    protected AbstractResponseMessage(final MessageId id,
                                      final String userAgent,
                                      final int networkId,
                                      final CompressedPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final CompressedPublicKey recipient,
                                      final MessageId correspondingId) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient);
        this.correspondingId = requireNonNull(correspondingId);
    }

    protected AbstractResponseMessage(final int networkId,
                                      final CompressedPublicKey sender,
                                      final ProofOfWork proofOfWork,
                                      final CompressedPublicKey recipient,
                                      final MessageId correspondingId) {
        super(networkId, sender, proofOfWork, recipient);
        this.correspondingId = requireNonNull(correspondingId);
    }

    @SuppressWarnings({ "java:S107" })
    public AbstractResponseMessage(final MessageId id,
                                   final String userAgent,
                                   final int networkId,
                                   final CompressedPublicKey sender,
                                   final ProofOfWork proofOfWork,
                                   final CompressedPublicKey recipient,
                                   final short hopCount,
                                   final MessageId correspondingId) {
        super(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount);
        this.correspondingId = requireNonNull(correspondingId);
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), correspondingId);
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
        final AbstractResponseMessage<?> that = (AbstractResponseMessage<?>) o;
        return Objects.equals(correspondingId, that.correspondingId);
    }
}