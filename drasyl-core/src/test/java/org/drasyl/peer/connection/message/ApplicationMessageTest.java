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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.testutils.IdentityRandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ApplicationMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    CompressedPublicKey senderPubKey;
    Identity sender;
    Identity recipient;
    private String id;
    private ApplicationMessage message;
    private short hopCount;

    @BeforeEach
    void setUp() {
        senderPubKey = mock(CompressedPublicKey.class);
        sender = IdentityRandomGenerator.random();
        recipient = IdentityRandomGenerator.random();
        id = "id";
        hopCount = 64;
    }

    @Test
    void toJson() throws JsonProcessingException {
        message = new ApplicationMessage(sender, recipient, new byte[]{
                0x00,
                0x01,
                0x02
        }, (short) 64);

        System.out.println(JSON_MAPPER.writeValueAsString(message));

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"ApplicationMessage\",\"id\":\"" + message.getId() + "\",\"recipient\":{\"publicKey\":\"" + recipient.getPublicKey().getCompressedKey() + "\"},\"hopCount\":64,\"sender\":{\"publicKey\":\"" + sender.getPublicKey().getCompressedKey() + "\"},\"payload\":\"AAEC\"}\n");
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"ApplicationMessage\",\"id\":\"123\",\"sender\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"recipient\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"payload\":\"AAEC\"}";

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

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void hashCodeNotSameBecauseOfDifferentPayload() {
        ApplicationMessage message1 = new ApplicationMessage(id, recipient, sender, new byte[]{
                0x00,
                0x01,
                0x02
        }, hopCount);
        ApplicationMessage message2 = new ApplicationMessage(id, recipient, sender, new byte[]{
                0x00,
                0x01,
                0x02
        }, hopCount);
        ApplicationMessage message3 = new ApplicationMessage(id, recipient, sender, new byte[]{
                0x03,
                0x02,
                0x01
        }, hopCount);

        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }

    @Test
    void incrementHopCountShouldIncrementHopCountByOne() {
        ApplicationMessage message = new ApplicationMessage(sender, recipient, new byte[]{});

        message.incrementHopCount();

        assertEquals(1, message.getHopCount());
    }
}
