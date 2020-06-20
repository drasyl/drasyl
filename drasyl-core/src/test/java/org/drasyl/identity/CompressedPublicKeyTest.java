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

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressedPublicKeyTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private CompressedPublicKey publicKey;

    @BeforeEach
    void setUp() throws CryptoException {
        publicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
    }

    @Nested
    class Of {
        @Test
        void shouldReturnCorrectKeys() throws CryptoException {
            CompressedPublicKey compressedPublicKey1 = publicKey;
            CompressedPublicKey compressedPublicKey2 = CompressedPublicKey.of(compressedPublicKey1.getCompressedKey());
            CompressedPublicKey compressedPublicKey3 = CompressedPublicKey.of(compressedPublicKey2.toUncompressedKey());

            assertEquals(compressedPublicKey1, compressedPublicKey2);
            assertEquals(compressedPublicKey1, compressedPublicKey3);
            assertEquals(compressedPublicKey2, compressedPublicKey3);
            assertEquals(compressedPublicKey1.hashCode(), compressedPublicKey2.hashCode());
            assertEquals(compressedPublicKey1.hashCode(), compressedPublicKey3.hashCode());
            assertEquals(compressedPublicKey2.hashCode(), compressedPublicKey3.hashCode());
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "\"0340A4F2ADBDDEEDC8F9ACE30E3F18713A3405F43F4871B4BAC9624FE80D2056A7\"";

            assertThat(JSON_MAPPER.readValue(json, CompressedPublicKey.class), instanceOf(CompressedPublicKey.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            assertThatJson(JSON_MAPPER.writeValueAsString(publicKey))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo(publicKey.toString());

            assertEquals(publicKey, JSON_MAPPER.readValue(JSON_MAPPER.writeValueAsString(publicKey), CompressedPublicKey.class));
        }
    }
}