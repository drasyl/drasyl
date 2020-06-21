/*
 * Copyright (c) 2020.
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
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_MAPPER;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressedPrivateKeyTest {
    private CompressedPrivateKey privateKey;

    @BeforeEach
    void setUp() throws CryptoException {
        privateKey = CompressedPrivateKey.of("03a3c01d41b6a7c31b081c9f1ee0f8cd5d11e7ceb784359b57388c752d38f581");
    }

    @Nested
    class Of {
        @Test
        void shouldReturnCorrectKeys() throws CryptoException {
            CompressedPrivateKey compressedPrivateKey1 = privateKey;
            CompressedPrivateKey compressedPrivateKey2 = CompressedPrivateKey.of(compressedPrivateKey1.getCompressedKey());
            CompressedPrivateKey compressedPrivateKey3 = CompressedPrivateKey.of(compressedPrivateKey2.toUncompressedKey());

            assertEquals(compressedPrivateKey1, compressedPrivateKey2);
            assertEquals(compressedPrivateKey1, compressedPrivateKey3);
            assertEquals(compressedPrivateKey2, compressedPrivateKey3);
            assertEquals(compressedPrivateKey1.hashCode(), compressedPrivateKey2.hashCode());
            assertEquals(compressedPrivateKey1.hashCode(), compressedPrivateKey3.hashCode());
            assertEquals(compressedPrivateKey2.hashCode(), compressedPrivateKey3.hashCode());
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "\"045ADCB39AA39A81E8C95A0E309B448FA60A41535B3F3CA41AA2745558DFFD6B\"";

            assertThat(JACKSON_MAPPER.readValue(json, CompressedPrivateKey.class), instanceOf(CompressedPrivateKey.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            assertThatJson(JACKSON_MAPPER.writeValueAsString(privateKey))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo(privateKey.toString());

            assertEquals(privateKey, JACKSON_MAPPER.readValue(JACKSON_MAPPER.writeValueAsString(privateKey), CompressedPrivateKey.class));
        }
    }
}