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
package org.drasyl.node.handler.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteSerializerTest {
    private ByteSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new ByteSerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeByteToByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(Byte.valueOf("123"));

            assertArrayEquals(new byte[]{ 123 }, bytes);
        }

        @Test
        void shouldThrowExceptionForNonByte() {
            assertThrows(IOException.class, () -> serializer.toByteArray(1337));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeByteArrayToByte() throws IOException {
            final Object o = serializer.fromByteArray(new byte[]{ 123 }, Byte.class);

            assertEquals(Byte.valueOf("123"), o);
        }

        @Test
        void shouldThrowExceptionForNonByte() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, Integer.class));
        }

        @Test
        void shouldThrowExceptionForInvalidByteArray() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{
                    63,
                    -85
            }, Byte.class));
        }
    }
}
