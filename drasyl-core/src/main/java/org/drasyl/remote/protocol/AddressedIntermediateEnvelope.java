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
package org.drasyl.remote.protocol;

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;

public class AddressedIntermediateEnvelope<T extends MessageLite> extends ReferenceCountedAddressedEnvelope<InetSocketAddressWrapper, IntermediateEnvelope<T>> {
    public AddressedIntermediateEnvelope(final InetSocketAddressWrapper sender,
                                         final InetSocketAddressWrapper recipient,
                                         final IntermediateEnvelope<T> content) {
        super(sender, recipient, content);
    }

    /**
     * @throws IllegalArgumentException if the given {@link ByteBuf} is not readable
     */
    public AddressedIntermediateEnvelope(final InetSocketAddressWrapper sender,
                                         final InetSocketAddressWrapper recipient,
                                         final ByteBuf byteBuf) {
        super(sender, recipient, IntermediateEnvelope.of(byteBuf));
    }

    @Override
    public String toString() {
        return "AddressedIntermediateEnvelope{" +
                "sender=" + getSender() +
                ", recipient=" + getRecipient() +
                ", content=" + getContent() +
                '}';
    }
}
