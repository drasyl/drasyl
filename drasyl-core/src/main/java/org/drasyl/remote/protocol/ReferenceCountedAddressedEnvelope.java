/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.remote.protocol;

import io.netty.util.ReferenceCounted;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;

abstract class ReferenceCountedAddressedEnvelope<A extends Address, M extends ReferenceCounted> extends DefaultAddressedEnvelope<A, M> implements ReferenceCounted {
    /**
     * @throws IllegalArgumentException if {@code sender} and {@code recipient} are {@code null}
     */
    protected ReferenceCountedAddressedEnvelope(final A sender,
                                                final A recipient,
                                                final M content) {
        super(sender, recipient, content);
    }

    @Override
    public int refCnt() {
        return getContent().refCnt();
    }

    @Override
    public ReferenceCounted retain() {
        return getContent().retain();
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        return getContent().retain(increment);
    }

    @Override
    public ReferenceCounted touch() {
        return getContent().touch();
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        return getContent().touch(hint);
    }

    @Override
    public boolean release() {
        return getContent().release();
    }

    @Override
    public boolean release(final int decrement) {
        return getContent().release(decrement);
    }
}
