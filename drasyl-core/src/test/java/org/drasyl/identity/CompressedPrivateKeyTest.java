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

class CompressedPrivateKeyTest {
    private CompressedPrivateKey privateKey;

    @BeforeEach
    void setUp() {
        privateKey = CompressedPrivateKey.of("03a3c01d41b6a7c31b081c9f1ee0f8cd5d11e7ceb784359b57388c752d38f581");
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
