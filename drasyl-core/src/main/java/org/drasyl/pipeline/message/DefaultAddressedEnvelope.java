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
package org.drasyl.pipeline.message;

import org.drasyl.pipeline.address.Address;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class DefaultAddressedEnvelope<A extends Address, M> implements AddressedEnvelope<A, M> {
    private final A sender;
    private final A recipient;
    private final M content;

    /**
     * @throws NullPointerException if {@code sender} and {@code recipient} are {@code null}
     */
    public DefaultAddressedEnvelope(final A sender, final A recipient, final M content) {
        if (sender == null && recipient == null) {
            throw new NullPointerException("recipient and sender");
        }
        this.sender = sender;
        this.recipient = recipient;
        this.content = requireNonNull(content);
    }

    @Override
    public A getSender() {
        return sender;
    }

    @Override
    public A getRecipient() {
        return recipient;
    }

    @Override
    public M getContent() {
        return content;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAddressedEnvelope<?, ?> that = (DefaultAddressedEnvelope<?, ?>) o;
        return Objects.equals(sender, that.sender) &&
                Objects.equals(recipient, that.recipient) &&
                Objects.deepEquals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, recipient, content);
    }

    @Override
    public String toString() {
        return "DefaultAddressedEnvelope{" +
                "sender=" + sender +
                ", recipient=" + recipient +
                ", content=" + content +
                '}';
    }
}
