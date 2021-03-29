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
package org.drasyl.identity;

import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressedPublicKeyTest {
    private CompressedPublicKey publicKey;

    @BeforeEach
    void setUp() {
        publicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
    }

    @Nested
    class Of {
        @Test
        void shouldReturnCorrectKeys() {
            final CompressedPublicKey compressedPublicKey1 = publicKey;
            final CompressedPublicKey compressedPublicKey2 = CompressedPublicKey.of(compressedPublicKey1.byteArrayValue());
            final CompressedPublicKey compressedPublicKey3 = CompressedPublicKey.of(compressedPublicKey2.byteArrayValue());

            assertEquals(compressedPublicKey1, compressedPublicKey2);
            assertEquals(compressedPublicKey1, compressedPublicKey3);
            assertEquals(compressedPublicKey2, compressedPublicKey3);
            assertEquals(compressedPublicKey1.hashCode(), compressedPublicKey2.hashCode());
            assertEquals(compressedPublicKey1.hashCode(), compressedPublicKey3.hashCode());
            assertEquals(compressedPublicKey2.hashCode(), compressedPublicKey3.hashCode());
        }

        @Test
        void shouldRejectInvalidKeys() {
            assertThrows(IllegalArgumentException.class, () -> CompressedPublicKey.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> CompressedPublicKey.of(""));
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "\"AikEGyc91e4cK+8td64X29ANLwouk54i1C7xxL8FFH6p\"";

            assertEquals(
                    CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"),
                    JACKSON_READER.readValue(json, CompressedPublicKey.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            assertThatJson(JACKSON_WRITER.writeValueAsString(publicKey))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo("AikEGyc91e4cK+8td64X29ANLwouk54i1C7xxL8FFH6p");
        }
    }
}
