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
import org.drasyl.crypto.Signature;
import org.drasyl.identity.Address;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.AddressTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ApplicationMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    KeyPair keyPair;
    CompressedPublicKey senderPubKey;
    Address sender;
    Address recipient;
    private String id;
    private Signature signature;
    private ApplicationMessage message;

    @BeforeEach
    void setUp() throws CryptoException {
        keyPair = Crypto.generateKeys();
        senderPubKey = CompressedPublicKey.of(keyPair.getPublic());
        sender = Address.of(senderPubKey);
        recipient = AddressTestHelper.random();
        id = "id";
        signature = mock(Signature.class);
    }

    @Test
    void toJson() throws JsonProcessingException {
        message = new ApplicationMessage(sender, recipient, new byte[]{ 0x00, 0x01, 0x02 });

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"ApplicationMessage\",\"id\":\"" + message.getId() + "\",\"sender\":\"" + sender + "\",\"recipient\":\"" + recipient + "\",\"payload\":\"AAEC\"}");
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"ApplicationMessage\",\"id\":\"" + id + "\",\"sender\":\"" + sender + "\",\"recipient\":\"" + recipient + "\",\"payload\":\"AAEC\"}";

        ApplicationMessage message = JSON_MAPPER.readValue(json, ApplicationMessage.class);

        assertThat(message, instanceOf(ApplicationMessage.class));
    }

    @Test
    void nullTest() {
        assertThrows(NullPointerException.class, () -> new ApplicationMessage(null, recipient, new byte[]{}), "Message requires a sender");

        assertThrows(NullPointerException.class, () -> new ApplicationMessage(sender, null, new byte[]{}), "Message requires a recipient");

        assertThrows(NullPointerException.class, () -> new ApplicationMessage(sender, recipient, null), "Message requires a payload");

        assertThrows(NullPointerException.class, () -> new ApplicationMessage(null, null, null), "Message requires a sender, a recipient and a payload");
    }

    @Test
    void equalsNotSameBecauseOfDifferentPayload() {
        ApplicationMessage message1 = new ApplicationMessage(sender, recipient, new byte[]{
                0x00,
                0x01,
                0x02
        });
        ApplicationMessage message2 = new ApplicationMessage(sender, recipient, new byte[]{
                0x00,
                0x01,
                0x02
        });
        ApplicationMessage message3 = new ApplicationMessage(sender, recipient, new byte[]{
                0x03,
                0x02,
                0x01
        });

        assertNotEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void hashCodeNotSameBecauseOfDifferentPayload() {
        ApplicationMessage message1 = new ApplicationMessage(id, signature, recipient, sender, new byte[]{
                0x00,
                0x01,
                0x02
        });
        ApplicationMessage message2 = new ApplicationMessage(id, signature, recipient, sender, new byte[]{
                0x00,
                0x01,
                0x02
        });
        ApplicationMessage message3 = new ApplicationMessage(id, signature, recipient, sender, new byte[]{
                0x03,
                0x02,
                0x01
        });

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
