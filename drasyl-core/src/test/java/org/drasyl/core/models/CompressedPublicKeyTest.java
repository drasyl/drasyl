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
package org.drasyl.core.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressedPublicKeyTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = Crypto.generateKeys();
    }

    @Test
    public void ofTest() throws CryptoException {
        CompressedPublicKey compressedPublicKey1 = CompressedPublicKey.of(keyPair.getPublic());
        CompressedPublicKey compressedPublicKey2 = CompressedPublicKey.of(compressedPublicKey1.getCompressedKey());
        CompressedPublicKey compressedPublicKey3 = CompressedPublicKey.of(compressedPublicKey2.toPubKey());

        assertEquals(compressedPublicKey1, compressedPublicKey2);
        assertEquals(compressedPublicKey1, compressedPublicKey3);
        assertEquals(compressedPublicKey2, compressedPublicKey3);
        assertEquals(compressedPublicKey1.hashCode(), compressedPublicKey2.hashCode());
        assertEquals(compressedPublicKey1.hashCode(), compressedPublicKey3.hashCode());
        assertEquals(compressedPublicKey2.hashCode(), compressedPublicKey3.hashCode());
    }

    @Test
    public void toJson() throws IOException, CryptoException {
        CompressedPublicKey compressedPublicKey = CompressedPublicKey.of(keyPair.getPublic());

        assertThatJson(JSON_MAPPER.writeValueAsString(compressedPublicKey))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo(compressedPublicKey.toString());

        // Ignore toString()
        compressedPublicKey.toString();
        assertEquals(compressedPublicKey, JSON_MAPPER.readValue(JSON_MAPPER.writeValueAsString(compressedPublicKey), CompressedPublicKey.class));
    }

    @Test
    public void fromJson() throws IOException {
        String json = "\"0340A4F2ADBDDEEDC8F9ACE30E3F18713A3405F43F4871B4BAC9624FE80D2056A7\"";

        assertThat(JSON_MAPPER.readValue(json, CompressedPublicKey.class), instanceOf(CompressedPublicKey.class));
    }
}