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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanSerializerTest {
    private BooleanSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new BooleanSerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeTrueToCorrectByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(true);

            assertArrayEquals(new byte[]{ 1 }, bytes);
        }

        @Test
        void shouldSerializeFalseToCorrectByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(false);

            assertArrayEquals(new byte[]{ 0 }, bytes);
        }

        @Test
        void shouldThrowExceptionForNonString() {
            assertThrows(IOException.class, () -> serializer.toByteArray(1337));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeTrueByteArrayToTrue() throws IOException {
            assertTrue(serializer.fromByteArray(new byte[]{ 1 }, Boolean.class));
        }

        @Test
        void shouldDeserializeFalseByteArrayToFalse() throws IOException {
            assertFalse(serializer.fromByteArray(new byte[]{ 0 }, Boolean.class));
        }

        @Test
        void shouldThrowExceptionForNonProtobufType() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, String.class));
        }
    }
}
