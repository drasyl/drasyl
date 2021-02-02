/*
 * Copyright (c) 2020-2021.
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

import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtobufSerializerTest {
    private ProtobufSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new ProtobufSerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeProtobufMessageToByteArray() throws IOException {
            PrivateHeader.parseFrom(new byte[]{});
            final byte[] bytes = serializer.toByteArray(PrivateHeader.newBuilder().build());

            assertArrayEquals(new byte[]{}, bytes);
        }

        @Test
        void shouldThrowExceptionForNonProtobufMessage() {
            assertThrows(IOException.class, () -> serializer.toByteArray("Hey!"));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeByteArrayToProtobufMessage() throws IOException {
            final Object o = serializer.fromByteArray(PublicHeader.newBuilder().setNetworkId(1337).build().toByteArray(), PublicHeader.class);

            assertEquals(PublicHeader.newBuilder().setNetworkId(1337).build(), o);
        }

        @Test
        void shouldThrowExceptionForNonProtobufType() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, String.class));
        }
    }
}
