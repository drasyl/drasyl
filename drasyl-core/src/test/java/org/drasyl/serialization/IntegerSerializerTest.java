/*
 * Copyright (c) 2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegerSerializerTest {
    private IntegerSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new IntegerSerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeIntegerToByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(1337);

            assertArrayEquals(new byte[]{ 0, 0, 5, 57 }, bytes);
        }

        @Test
        void shouldThrowExceptionForNonInteger() {
            assertThrows(IOException.class, () -> serializer.toByteArray("Hallo Welt"));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeByteArrayToInteger() throws IOException {
            final Object o = serializer.fromByteArray(new byte[]{ 0, 0, 5, 57 }, Integer.class);

            assertEquals(1337, o);
        }

        @Test
        void shouldThrowExceptionForNonInteger() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, String.class));
        }

        @Test
        void shouldThrowExceptionForInvalidByteArray() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{
                    5,
                    57
            }, Integer.class));
        }
    }
}