/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.rmi.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

/**
 * A invocation of a remote method.
 *
 * @see RmiResponse
 * @see RmiCancel
 * @see RmiError
 */
public final class RmiRequest extends DefaultByteBufHolder implements RmiMessage {
    private final UUID id;
    private final int name;
    private final int method;

    private RmiRequest(final UUID id, final int name, final int method, final ByteBuf arguments) {
        super(arguments);
        this.id = requireNonNull(id);
        this.name = name;
        this.method = method;
    }

    public static RmiRequest of(final UUID id,
                                final int name,
                                final int method,
                                final ByteBuf arguments) {
        return new RmiRequest(id, name, method, arguments);
    }

    public static RmiRequest of(final int name,
                                final int method,
                                final ByteBuf arguments) {
        return of(randomUUID(), name, method, arguments);
    }

    public UUID getId() {
        return id;
    }

    public int getName() {
        return name;
    }

    public int getMethod() {
        return method;
    }

    public ByteBuf getArguments() {
        return content();
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
        final RmiRequest that = (RmiRequest) o;
        return name == that.name && method == that.method && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, method, super.hashCode());
    }

    @Override
    public String toString() {
        return "RmiRequest{" +
                "id=" + id +
                ", name=" + name +
                ", method=" + method +
                ", arguments=" + getArguments() +
                '}';
    }
}
