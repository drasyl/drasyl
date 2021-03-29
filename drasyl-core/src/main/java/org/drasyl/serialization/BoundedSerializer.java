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
package org.drasyl.serialization;

import org.drasyl.util.TypeParameterMatcher;

import java.io.IOException;

@SuppressWarnings("java:S118")
abstract class BoundedSerializer<B> implements Serializer {
    private final TypeParameterMatcher matcher;

    protected BoundedSerializer() {
        matcher = TypeParameterMatcher.find(this, BoundedSerializer.class, "B");
    }

    @SuppressWarnings("unchecked")
    @Override
    public byte[] toByteArray(final Object o) throws IOException {
        if (matcher.match(o)) {
            return matchedToByArray((B) o);
        }
        else {
            throw new IOException("Object must be of type `" + matcher.getType().getName() + "`");
        }
    }

    protected abstract byte[] matchedToByArray(final B o) throws IOException;

    @SuppressWarnings("unchecked")
    @Override
    public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
        if (matcher.matchClass(type)) {
            return (T) matchedFromByteArray(bytes, (Class<B>) type);
        }
        else {
            throw new IOException("Type must be a subclass of `" + matcher.getType().getName() + "`");
        }
    }

    protected abstract B matchedFromByteArray(final byte[] bytes, Class<B> type) throws IOException;
}
