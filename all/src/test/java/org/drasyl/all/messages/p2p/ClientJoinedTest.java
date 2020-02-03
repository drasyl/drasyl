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
import org.drasyl.all.models.SessionChannel;
import org.drasyl.all.models.SessionUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class ClientJoinedTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        ClientJoined message = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"ClientJoined\",\"messageID\":\"" + message.getMessageID() + "\",\"clientUID\":\"junit\",\"sessionChannels\":[\"testChannel\",\"testChannel2\"]}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"ClientJoined\",\"messageID\":\"C3162F87E0F2ED0A216D10B6\",\"clientUID\":\"junit\",\"sessionChannels\":[\"testChannel\",\"testChannel2\"]}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ClientJoined.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new ClientJoined(null, Set.of());
        }, "ClientJoined requires a clientUID");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new ClientJoined(SessionUID.of("test"), null);
        }, "ClientJoined requires a channels set");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new ClientJoined(null, null);
        }, "ClientJoined requires a clientUID and a channels set");
    }

    @Test
    void testEquals() {
        ClientJoined message1 = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        ClientJoined message2 = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        ClientJoined message3 = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel3")));

        assertTrue(message1.equals(message2));
        assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        ClientJoined message1 = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        ClientJoined message2 = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        ClientJoined message3 = new ClientJoined(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel3")));

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
