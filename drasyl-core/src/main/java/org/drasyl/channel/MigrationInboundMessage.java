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

import org.drasyl.pipeline.address.Address;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper used to add {@link Handler} to a {@link io.netty.channel.Channel}.
 */
public class MigrationInboundMessage<T, A extends Address> {
    private final T message;
    private final A address;
    private final CompletableFuture<Void> future;

    public MigrationInboundMessage(final T message,
                                   final A address,
                                   final CompletableFuture<Void> future) {
        this.message = message;
        this.address = requireNonNull(address);
        this.future = requireNonNull(future);
    }

    @Override
    public String toString() {
        return "MigrationInboundMessage{" +
                "message=" + message +
                ", address=" + address +
                '}';
    }

    public MigrationInboundMessage(final T message,
                                   final A address) {
        this(message, address, new CompletableFuture<>());
    }

    public T message() {
        return message;
    }

    public A address() {
        return address;
    }

    public CompletableFuture<Void> future() {
        return future;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MigrationInboundMessage<?, ?> that = (MigrationInboundMessage<?, ?>) o;
        return Objects.equals(message, that.message) && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, address);
    }
}
