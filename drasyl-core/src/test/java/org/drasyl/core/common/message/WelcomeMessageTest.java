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
package org.drasyl.core.common.message;

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

public class WelcomeMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private CompressedPublicKey publicKey;
    private Set<URI> endpoints;
    private String correspondingId;

    @BeforeEach
    void setUp() throws CryptoException {
        AbstractMessageWithUserAgent.userAgentGenerator = () -> "";
        KeyPair keyPair = Crypto.generateKeys();
        publicKey = CompressedPublicKey.of(keyPair.getPublic());
        endpoints = Set.of(URI.create("ws://test"));
        correspondingId = "correspondingId";
    }

    @AfterEach
    public void tearDown() {
        AbstractMessageWithUserAgent.userAgentGenerator = AbstractMessageWithUserAgent.defaultUserAgentGenerator;
    }

    @Test
    public void toJson() throws JsonProcessingException {
        WelcomeMessage message = new WelcomeMessage(publicKey, endpoints, correspondingId);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"WelcomeMessage\",\"id\":\"" + message.getId() + "\",\"userAgent\":\"\",\"publicKey\":\"" + publicKey.getCompressedKey() + "\",\"endpoints\":[\"ws://test\"],\"correspondingId\":\"correspondingId\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"@type\":\"WelcomeMessage\",\"id\":\"4AE5CDCD8C21719F8E779F21\",\"userAgent\":\"\",\"publicKey\":\"" + publicKey.getCompressedKey() + "\",\"endpoints\":[\"ws://test\"]}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(WelcomeMessage.class));
    }

    @Test
    void testEquals() {
        WelcomeMessage message1 = new WelcomeMessage(publicKey, endpoints, correspondingId);
        WelcomeMessage message2 = new WelcomeMessage(publicKey, endpoints, correspondingId);

        assertEquals(message1, message2);
    }

    @Test
    void testHashCode() {
        WelcomeMessage message1 = new WelcomeMessage(publicKey, endpoints, correspondingId);
        WelcomeMessage message2 = new WelcomeMessage(publicKey, endpoints, correspondingId);

        assertEquals(message1.hashCode(), message2.hashCode());
    }
}
