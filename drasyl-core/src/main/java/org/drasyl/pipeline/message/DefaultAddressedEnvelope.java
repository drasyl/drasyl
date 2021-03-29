/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.pipeline.message;

import org.drasyl.pipeline.address.Address;

import java.util.Objects;

public class DefaultAddressedEnvelope<A extends Address, M> implements AddressedEnvelope<A, M> {
    private final A sender;
    private final A recipient;
    private final M content;

    /**
     * @throws IllegalArgumentException if {@code sender} and {@code recipient} are {@code null}
     */
    public DefaultAddressedEnvelope(final A sender, final A recipient, final M content) {
        if (sender == null && recipient == null) {
            throw new IllegalArgumentException("sender and receiver must not both be null.");
        }
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
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
