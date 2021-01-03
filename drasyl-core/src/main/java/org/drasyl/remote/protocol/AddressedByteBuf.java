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

import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;

import java.net.InetSocketAddress;

public class AddressedByteBuf extends ReferenceCountedAddressedEnvelope<InetSocketAddressWrapper, ByteBuf> {
    /**
     * @throws NullPointerException if {@code sender} and {@code recipient} are {@code null}
     */
    public AddressedByteBuf(final InetSocketAddressWrapper sender,
                            final InetSocketAddressWrapper recipient,
                            final ByteBuf content) {
        super(sender, recipient, content);
    }

    /**
     * @throws NullPointerException if {@code sender} or {@code recipient} is {@code null}
     */
    public AddressedByteBuf(final InetSocketAddress sender,
                            final InetSocketAddress recipient,
                            final ByteBuf content) {
        this(InetSocketAddressWrapper.of(sender), InetSocketAddressWrapper.of(recipient), content);
    }

    @Override
    public String toString() {
        return "AddressedByteBuf{" +
                "sender=" + getSender() +
                ", recipient=" + getRecipient() +
                ", content=" + getContent() +
                '}';
    }
}
