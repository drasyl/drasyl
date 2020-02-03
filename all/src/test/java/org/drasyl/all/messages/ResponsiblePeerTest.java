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

package org.drasyl.all.messages;

import org.drasyl.all.models.IPAddress;
import org.drasyl.all.models.SessionUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.drasyl.all.models.SessionUID.of;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ResponsiblePeerTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private Join join;

    @BeforeEach
    void setUp() {
        UserAgentMessage.userAgentGenerator = () -> "";
        join = mock(Join.class);
    }

    @AfterEach
    public void tearDown() {
        UserAgentMessage.userAgentGenerator = UserAgentMessage.defaultUserAgentGenerator;
    }

    @Test
    public void toJson() throws JsonProcessingException {
        ResponsiblePeer message = new ResponsiblePeer(new IPAddress("127.0.0.1", 80), SessionUID.of("id"), SessionUID.of("abc"));

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"ResponsiblePeer\",\"messageID\":\"" + message.getMessageID() + "\",\"join\":{\"type\":\"Join\",\"messageID\":\"" + message.getJoin().getMessageID() + "\",\"userAgent\":\"\",\"clientUID\":\"id\",\"sessionChannels\":[],\"relayUID\":\"abc\",\"rpmID\":\"" + message.getJoin().getRpmID() + "\"},\"host\":\"127.0.0.1:80\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"ResponsiblePeer\",\"messageID\":\"DF3106EC19F5F32171ADC064\",\"join\":{\"type\":\"Join\",\"messageID\":\"F5CE9208BDBDD914DE90E6A0\",\"userAgent\":\"\",\"clientUID\":\"id\",\"sessionChannels\":[],\"relayUID\":\"abc\",\"rpmID\":\"DF3106EC19F5F32171ADC064\"},\"host\":{\"host\":\"127.0.0.1:80\"}}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ResponsiblePeer.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new ResponsiblePeer(null);
        }, "ResponsiblePeerMessage requires an ipAddress");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new ResponsiblePeer(null, of("id"), of("abc"));
        }, "ResponsiblePeerMessage requires an ipAddress");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new ResponsiblePeer(new IPAddress("127.0.0.1", 80), null, of("abc"));
        }, "ResponsiblePeerMessage requires a clientUID");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new ResponsiblePeer(new IPAddress("127.0.0.1", 80), of("id"), null);
        }, "ResponsiblePeerMessage requires a relayUID");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new ResponsiblePeer(null, null, null);
        }, "ResponsiblePeerMessage requires an ipAddress, a clientUID and a relayUID");
    }

    @Test
    void testEquals() {
        ResponsiblePeer message1 = new ResponsiblePeer(new IPAddress("127.0.0.1", 80), join);
        ResponsiblePeer message2 = new ResponsiblePeer(new IPAddress("127.0.0.1", 80), join);
        ResponsiblePeer message3 = new ResponsiblePeer(new IPAddress("127.0.0.1", 443), join);

        assertTrue(message1.equals(message2));
        assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        ResponsiblePeer message1 = new ResponsiblePeer(new IPAddress("127.0.0.1", 80), join);
        ResponsiblePeer message2 = new ResponsiblePeer(new IPAddress("127.0.0.1", 80), join);
        ResponsiblePeer message3 = new ResponsiblePeer(new IPAddress("127.0.0.1", 443), join);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
