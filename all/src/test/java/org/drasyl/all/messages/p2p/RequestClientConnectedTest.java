/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.all.messages.p2p;

import org.drasyl.all.messages.Message;
import org.drasyl.all.models.SessionUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class RequestClientConnectedTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        RequestClientConnected message = new RequestClientConnected(SessionUID.of("clientUID"));

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"RequestClientConnected\",\"messageID\":\"" + message.getMessageID() + "\",\"clientUID\":\"clientUID\"}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"RequestClientConnected\",\"messageID\":\"77175D7235920F3BA17341D7\",\"clientUID\":\"clientUID\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(RequestClientConnected.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new RequestClientConnected(null);
        }, "RequestClientConnected requires a client UID");
    }

    @Test
    void testEquals() {
        RequestClientConnected message1 = new RequestClientConnected(SessionUID.of("clientUID"));
        RequestClientConnected message2 = new RequestClientConnected(SessionUID.of("clientUID"));
        RequestClientConnected message3 = new RequestClientConnected(SessionUID.of("clientUID2"));

        assertTrue(message1.equals(message2));
        assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        RequestClientConnected message1 = new RequestClientConnected(SessionUID.of("clientUID"));
        RequestClientConnected message2 = new RequestClientConnected(SessionUID.of("clientUID"));
        RequestClientConnected message3 = new RequestClientConnected(SessionUID.of("clientUID2"));

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
