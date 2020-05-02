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
package org.drasyl.core.common.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class WelcomeTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private CompressedPublicKey publicKey;
    private Set<URI> endpoints;

    @BeforeEach
    void setUp() throws CryptoException {
        UserAgentMessage.userAgentGenerator = () -> "";
        KeyPair keyPair = Crypto.generateKeys();
        publicKey = CompressedPublicKey.of(keyPair.getPublic());
        endpoints = Set.of(URI.create("ws://test"));
    }

    @AfterEach
    public void tearDown() {
        UserAgentMessage.userAgentGenerator = UserAgentMessage.defaultUserAgentGenerator;
    }

    @Test
    public void toJson() throws JsonProcessingException {
        Welcome message = new Welcome(publicKey, endpoints);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Welcome\",\"messageID\":\"" + message.getMessageID() + "\",\"userAgent\":\"\",\"publicKey\":\"" + publicKey.getCompressedKey() + "\",\"endpoints\":[\"ws://test\"],\"signature\":null}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Welcome\",\"messageID\":\"4AE5CDCD8C21719F8E779F21\",\"userAgent\":\"\",\"publicKey\":\"" + publicKey.getCompressedKey() + "\",\"endpoints\":[\"ws://test\"],\"signature\":null}";

        assertThat(JSON_MAPPER.readValue(json, IMessage.class), instanceOf(Welcome.class));
    }

    @Test
    void testEquals() {
        Welcome message1 = new Welcome(publicKey, endpoints);
        Welcome message2 = new Welcome(publicKey, endpoints);

        assertEquals(message1, message2);
    }

    @Test
    void testHashCode() {
        Welcome message1 = new Welcome(publicKey, endpoints);
        Welcome message2 = new Welcome(publicKey, endpoints);

        assertEquals(message1.hashCode(), message2.hashCode());
    }
}
