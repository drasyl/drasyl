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
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class RequestClientsStocktakingMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        RequestClientsStocktakingMessage message = new RequestClientsStocktakingMessage();

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"RequestClientsStocktakingMessage\",\"id\":\"" + message.getId() + "\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"@type\":\"RequestClientsStocktakingMessage\",\"id\":\"77175D7235920F3BA17341D7\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(RequestClientsStocktakingMessage.class));
    }

    @Test
    void testEquals() {
        RequestClientsStocktakingMessage message1 = new RequestClientsStocktakingMessage();
        RequestClientsStocktakingMessage message2 = new RequestClientsStocktakingMessage();

        assertEquals(message1, message2);
    }

    @Test
    void testHashCode() {
        RequestClientsStocktakingMessage message1 = new RequestClientsStocktakingMessage();
        RequestClientsStocktakingMessage message2 = new RequestClientsStocktakingMessage();

        assertEquals(message1.hashCode(), message2.hashCode());
    }
}
