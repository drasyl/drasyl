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
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JoinMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private CompressedPublicKey publicKey;
    private Set<CompressedPublicKey> childrenAndGrandchildren;
    private CompressedPublicKey grandchildrenPublicKey;
    private CompressedPublicKey publicKey2;
    private ProofOfWork proofOfWork1;
    private ProofOfWork proofOfWork2;

    @BeforeEach
    void setUp() throws CryptoException {
        AbstractMessageWithUserAgent.userAgentGenerator = () -> "";
        publicKey = CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d");
        publicKey2 = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        proofOfWork1 = ProofOfWork.of(3556154);
        proofOfWork2 = ProofOfWork.of(16425882);
        grandchildrenPublicKey = CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458");
        childrenAndGrandchildren = Set.of(grandchildrenPublicKey);
    }

    @AfterEach
    public void tearDown() {
        AbstractMessageWithUserAgent.userAgentGenerator = AbstractMessageWithUserAgent.defaultUserAgentGenerator;
    }

    @Test
    void toJson() throws JsonProcessingException {
        JoinMessage message = new JoinMessage(proofOfWork1, publicKey, childrenAndGrandchildren);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"JoinMessage\",\"id\":\"" + message.getId() + "\",\"userAgent\":\"\",\"proofOfWork\":{\"nonce\":3556154},\"publicKey\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\",\"childrenAndGrandchildren\":[\"0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458\"]}");

        // Ignore toString()
        message.toString();
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"JoinMessage\",\"id\":\"4ae5cdcd8c21719f8e779f21\",\"userAgent\":\"\",\"proofOfWork\":{\"nonce\":3556154},\"publicKey\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\", \"childrenAndGrandchildren\":[\"0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458\"]}";

        assertEquals(new JoinMessage(proofOfWork1, publicKey, Set.of(grandchildrenPublicKey)), JSON_MAPPER.readValue(json, Message.class));
    }

    @Test
    void nullTest() {
        assertThrows(NullPointerException.class, () -> new JoinMessage(proofOfWork1, null, childrenAndGrandchildren), "Join requires a public key");

        assertThrows(NullPointerException.class, () -> new JoinMessage(null, null, childrenAndGrandchildren), "Join requires a public key and endpoints");
    }

    @Test
    void testEquals() {
        JoinMessage message1 = new JoinMessage(proofOfWork1, publicKey, childrenAndGrandchildren);
        JoinMessage message2 = new JoinMessage(proofOfWork1, publicKey, childrenAndGrandchildren);
        JoinMessage message3 = new JoinMessage(proofOfWork2, publicKey2, Set.of());

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        JoinMessage message1 = new JoinMessage(proofOfWork1, publicKey, childrenAndGrandchildren);
        JoinMessage message2 = new JoinMessage(proofOfWork1, publicKey, childrenAndGrandchildren);
        JoinMessage message3 = new JoinMessage(proofOfWork2, publicKey2, Set.of());

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
