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

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * A Serializer represents a bimap between an object and an array of bytes representing that
 * object.
 *
 * <p>Make sure that your implementation implements the standard constructor!
 */
public interface Serializer {
    /**
     * Serializes the given object into an array of bytes
     *
     * @throws IOException if deserialization from byte array fails
     */
    byte[] toByteArray(Object o) throws IOException;

    /**
     * Produces an object of type {@code T} from an array of bytes.
     *
     * @throws IOException if serialization to byte array fails
     */
    <T> T fromByteArray(byte[] bytes, Class<T> type) throws IOException;

    @SuppressWarnings("java:S2658")
    default Object fromByteArray(final byte[] bytes, final String typeName) throws IOException {
        try {
            return fromByteArray(bytes, !isNullOrEmpty(typeName) ? Class.forName(typeName) : null);
        }
        catch (final ClassNotFoundException e) {
            throw new IOException("Class with name `" + typeName + "` could not be located.", e);
        }
    }
}
