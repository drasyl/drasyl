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

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

import static java.util.UUID.randomUUID;

@AutoValue
public abstract class RmiRequest implements RmiMessage {
    public abstract UUID getId();

    public abstract int getName();

    public abstract int getMethod();

    public abstract ByteBuf getArguments();

    public static RmiRequest of(final UUID id,
                                final int name,
                                final int method,
                                final ByteBuf arguments) {
        return new AutoValue_RmiRequest(id, name, method, arguments);
    }

    public static RmiRequest of(final int name,
                                final int method,
                                final ByteBuf arguments) {
        return of(randomUUID(), name, method, arguments);
    }
}
