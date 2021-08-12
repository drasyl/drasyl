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
package org.drasyl.channel;

import io.netty.util.ReferenceCounted;

import java.net.SocketAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message that wraps another message with an address.
 *
 * @param <M> the type of the wrapped message
 * @param <A> the type of the address
 */
public class AddressedMessage<M, A extends SocketAddress> implements ReferenceCounted {
    private final M message;
    private final A address;

    /**
     * @throws NullPointerException if {@code address} is {@code null}
     */
    public AddressedMessage(final M message, final A address) {
        this.message = message;
        this.address = requireNonNull(address);
    }

    /**
     * Returns the message wrapped by this addressed message.
     */
    public M message() {
        return message;
    }

    /**
     * Returns the address of this message.
     */
    public A address() {
        return address;
    }

    @Override
    public String toString() {
        return "AddressedMessage{" +
                "message=" + message +
                ", address=" + address +
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
        final AddressedMessage<?, ?> that = (AddressedMessage<?, ?>) o;
        return Objects.equals(message, that.message) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, address);
    }

    @Override
    public int refCnt() {
        if (message instanceof ReferenceCounted) {
            return ((ReferenceCounted) message).refCnt();
        }
        else {
            return 0;
        }
    }

    @Override
    public ReferenceCounted retain() {
        if (message instanceof ReferenceCounted) {
            ((ReferenceCounted) message).retain();
        }
        return this;
    }

    @Override
    public ReferenceCounted retain(final int increment) {
        if (message instanceof ReferenceCounted) {
            ((ReferenceCounted) message).retain(increment);
        }
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        if (message instanceof ReferenceCounted) {
            ((ReferenceCounted) message).touch();
        }
        return this;
    }

    @Override
    public ReferenceCounted touch(final Object hint) {
        if (message instanceof ReferenceCounted) {
            ((ReferenceCounted) message).touch(hint);
        }
        return this;
    }

    @Override
    public boolean release() {
        if (message instanceof ReferenceCounted) {
            return ((ReferenceCounted) message).release();
        }
        else {
            return false;
        }
    }

    @Override
    public boolean release(final int decrement) {
        if (message instanceof ReferenceCounted) {
            return ((ReferenceCounted) message).release(decrement);
        }
        else {
            return false;
        }
    }
}
