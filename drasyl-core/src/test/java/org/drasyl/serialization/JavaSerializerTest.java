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

import org.drasyl.crypto.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaSerializerTest {
    private JavaSerializer serializer;
    private byte[] serializedString;

    @BeforeEach
    void setUp() {
        serializer = new JavaSerializer();
        serializedString = HexUtil.fromString("aced000574000a48616c6c6f2057656c74");
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeObjectToByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray("Hallo Welt");

            assertArrayEquals(serializedString, bytes);
        }

        @Test
        void shouldThrowExceptionForNonSerializable() {
            assertThrows(IOException.class, () -> serializer.toByteArray(new CompletableFuture<>()));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeByteArrayToObject() throws IOException {
            assertEquals("Hallo Welt", serializer.fromByteArray(serializedString, String.class));
        }

        @Test
        void shouldThrowExceptionForNonString() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, CompletableFuture.class));
        }

        @Test
        void shouldThrowExceptionForInvalidByteArray() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, Integer.class));
        }
    }
}