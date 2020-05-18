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
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClientsStocktakingMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private Identity identity;
    private String correspondingId;

    @BeforeEach
    void setUp() {
        identity = Identity.of("ead3151c64");
        correspondingId = "correspondingId";
    }

    @Test
    void toJson() throws JsonProcessingException {
        ClientsStocktakingMessage message = new ClientsStocktakingMessage(List.of(identity), correspondingId);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"ClientsStocktakingMessage\",\"id\":\"" + message.getId() + "\",\"correspondingId\":\"correspondingId\",\"identities\":[\"" + identity.getId() + "\"]}");

        // Ignore toString()
        message.toString();
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"ClientsStocktakingMessage\",\"id\":\"FDCC13C730368155EC025866\",\"identities\":[\"" + identity.getId() + "\"],\"correspondingId\":\"correspondingId\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ClientsStocktakingMessage.class));
    }

    @Test
    void nullTest() {
        assertThrows(NullPointerException.class, () -> new ClientsStocktakingMessage(null, correspondingId), "ClientsStocktaking requires client UIDs");
    }

    @Test
    void testEquals() {
        ClientsStocktakingMessage message1 = new ClientsStocktakingMessage(List.of(identity), correspondingId);
        ClientsStocktakingMessage message2 = new ClientsStocktakingMessage(List.of(identity), correspondingId);
        ClientsStocktakingMessage message3 = new ClientsStocktakingMessage(List.of(IdentityTestHelper.random()), correspondingId);

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        ClientsStocktakingMessage message1 = new ClientsStocktakingMessage(List.of(identity), correspondingId);
        ClientsStocktakingMessage message2 = new ClientsStocktakingMessage(List.of(identity), correspondingId);
        ClientsStocktakingMessage message3 = new ClientsStocktakingMessage(List.of(IdentityTestHelper.random()), correspondingId);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
