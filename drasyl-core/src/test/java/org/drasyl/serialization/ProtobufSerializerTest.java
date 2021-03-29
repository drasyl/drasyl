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
