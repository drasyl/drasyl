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
package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Base64;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignedMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static KeyPair keyPair;

    @Test
    void toJson() throws JsonProcessingException, CryptoException {
        PingMessage message = new PingMessage();
        SignedMessage signedMessage = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));
        Crypto.sign(keyPair.getPrivate(), signedMessage);

        assertThatJson(JSON_MAPPER.writeValueAsString(signedMessage))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"SignedMessage\",\"payload\":{\"@type\":\"PingMessage\",\"id\":\"" + message.getId() + "\"},\"kid\":\"" + signedMessage.getKid() + "\",\"signature\": {\"bytes\":\"" + Base64.getEncoder().encodeToString(signedMessage.getSignature().getBytes()) + "\"}}");

        // Ignore toString()
        signedMessage.toString();
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"SignedMessage\",\"payload\":{\"@type\":\"PingMessage\",\"id\":\"05155f021bed10398c4ca71c\"},\"kid\":\"0313c96bed7252c22218972dd21d611fec413d76e9eaac2717ed76889dcd357edf\",\"signature\":{\"bytes\":\"MEQCIAESpHbepeb9cRDA5Hd0GErCQnpSj+GN2bQIFEO2AgN5AiAGJwpL9G2BEki8c+VdCcnvKloYXKCDYJSTYt3e5VTylw==\"}}\n";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(SignedMessage.class));
    }

    @Test
    void testEquals() throws CryptoException {
        PingMessage message = new PingMessage();
        SignedMessage signedMessage1 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));
        SignedMessage signedMessage2 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));

        assertEquals(signedMessage1, signedMessage2);
    }

    @Test
    void testHashCode() throws CryptoException {
        PingMessage message = new PingMessage();
        SignedMessage signedMessage1 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));
        SignedMessage signedMessage2 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));

        assertEquals(signedMessage1.hashCode(), signedMessage2.hashCode());
    }

    @BeforeAll
    static void setUp() {
        keyPair = Crypto.generateKeys();
    }
}