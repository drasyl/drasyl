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
import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.SessionChannel;
import org.drasyl.all.models.SessionUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class NetworkPeersTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        NetworkPeers message = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 80)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"NetworkPeers\",\"messageID\":\"" + message.getMessageID() + "\",\"peers\":{\"junit\":\"host:80\"},\"clients\":{\"junit\":[\"testChannel\",\"testChannel2\"]}}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"NetworkPeers\",\"messageID\":\"BC63C8925FC42364B9E8127A\",\"peers\":{\"junit\":\"host:80\"},\"clients\":{\"junit\":[\"testChannel\",\"testChannel2\"]}}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(NetworkPeers.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new NetworkPeers(null, Map.of());
        }, "NetworkPeers requires peers");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new NetworkPeers(Map.of(), null);
        }, "NetworkPeers requires clients");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new NetworkPeers(null, null);
        }, "NetworkPeers requires peers and clients");
    }

    @Test
    void testEquals() {
        NetworkPeers message1 = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 80)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );
        NetworkPeers message2 = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 80)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );
        NetworkPeers message3 = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 81)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );

        assertTrue(message1.equals(message2));
        assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        NetworkPeers message1 = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 80)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );
        NetworkPeers message2 = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 80)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );
        NetworkPeers message3 = new NetworkPeers(
                Map.of(SessionUID.of("junit"), new IPAddress("host", 81)),
                Map.of(SessionUID.of("junit"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")))
        );

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
