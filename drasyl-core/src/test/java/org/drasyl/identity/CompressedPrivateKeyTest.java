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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressedPrivateKeyTest {
    private CompressedPrivateKey privateKey;

    @BeforeEach
    void setUp() {
        privateKey = CompressedPrivateKey.of("03a3c01d41b6a7c31b081c9f1ee0f8cd5d11e7ceb784359b57388c752d38f581");
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnMaskedKey() {
            assertThat(privateKey.toString(), not(containsString(privateKey.toUnmaskedString())));
        }
    }

    @Nested
    class ToUnmaskedString {
        @Test
        void shouldReturnUnmaskedKey() {
            assertEquals("03a3c01d41b6a7c31b081c9f1ee0f8cd5d11e7ceb784359b57388c752d38f581", privateKey.toUnmaskedString());
        }
    }

    @Nested
    class Of {
        @Test
        void shouldReturnCorrectKeys() {
            final CompressedPrivateKey compressedPrivateKey1 = privateKey;
            final CompressedPrivateKey compressedPrivateKey2 = CompressedPrivateKey.of(compressedPrivateKey1.byteArrayValue());
            final CompressedPrivateKey compressedPrivateKey3 = CompressedPrivateKey.of(compressedPrivateKey2.byteArrayValue());

            assertEquals(compressedPrivateKey1, compressedPrivateKey2);
            assertEquals(compressedPrivateKey1, compressedPrivateKey3);
            assertEquals(compressedPrivateKey2, compressedPrivateKey3);
            assertEquals(compressedPrivateKey1.hashCode(), compressedPrivateKey2.hashCode());
            assertEquals(compressedPrivateKey1.hashCode(), compressedPrivateKey3.hashCode());
            assertEquals(compressedPrivateKey2.hashCode(), compressedPrivateKey3.hashCode());
        }

        @Test
        void shouldRejectInvalidKeys() {
            assertThrows(IllegalArgumentException.class, () -> CompressedPrivateKey.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> CompressedPrivateKey.of(""));
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "\"A6PAHUG2p8MbCByfHuD4zV0R5863hDWbVziMdS049YE=\"";

            assertEquals(
                    CompressedPrivateKey.of("03a3c01d41b6a7c31b081c9f1ee0f8cd5d11e7ceb784359b57388c752d38f581"),
                    JACKSON_READER.readValue(json, CompressedPrivateKey.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            assertThatJson(JACKSON_WRITER.writeValueAsString(privateKey))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo("A6PAHUG2p8MbCByfHuD4zV0R5863hDWbVziMdS049YE=");
        }
    }
}
