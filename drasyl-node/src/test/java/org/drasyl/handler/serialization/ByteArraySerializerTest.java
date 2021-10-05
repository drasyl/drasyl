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
package org.drasyl.handler.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteArraySerializerTest {
    private ByteArraySerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new ByteArraySerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldPassThroughByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(new byte[]{ 1, 3, 3, 7 });

            assertArrayEquals(new byte[]{ 1, 3, 3, 7 }, bytes);
        }

        @Test
        void shouldThrowExceptionForNonByteArray() {
            assertThrows(IOException.class, () -> serializer.toByteArray(1337));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldPassThroughByteArray() throws IOException {
            final byte[] bytes = serializer.fromByteArray(new byte[]{ 1, 3, 3, 7 }, byte[].class);

            assertArrayEquals(new byte[]{ 1, 3, 3, 7 }, bytes);
        }

        @Test
        void shouldThrowExceptionForNonByteArray() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, Integer.class));
        }
    }
}
