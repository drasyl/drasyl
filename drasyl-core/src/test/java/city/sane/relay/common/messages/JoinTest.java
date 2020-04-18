/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.common.messages;

import city.sane.relay.common.models.SessionChannel;
import city.sane.relay.common.models.SessionUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class JoinTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        UserAgentMessage.userAgentGenerator = () -> "";
    }

    @AfterEach
    public void tearDown() {
        UserAgentMessage.userAgentGenerator = UserAgentMessage.defaultUserAgentGenerator;
    }

    @Test
    public void toJson() throws JsonProcessingException {
        Join message = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Join\",\"messageID\":\"" + message.getMessageID() + "\",\"userAgent\":\"\",\"clientUID\":\"test\",\"sessionChannels\":[\"testChannel\",\"testChannel2\"],\"relayUID\":null,\"rpmID\":null}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Join\",\"messageID\":\"4AE5CDCD8C21719F8E779F21\",\"userAgent\":\"\",\"clientUID\":\"test\",\"sessionChannels\":[\"testChannel\",\"testChannel2\"],\"relayUID\":null,\"rpmID\":null}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(Join.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new Join(SessionUID.of("test"), null);
        }, "Join requires channels");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new Join(null, Set.of());
        }, "Join requires a clientUID");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new Join(null, null);
        }, "Join requires a clientUID and channels");
    }

    @Test
    public void invalidClientUIDTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new Join(SessionUID.of("junit#test"), Set.of());
        }, "Join requires a valid clientUID");
    }

    @Test
    void testEquals() {
        Join message1 = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        Join message2 = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        Join message3 = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel2"), SessionChannel.of("testChannel3")));

        Assert.assertTrue(message1.equals(message2));
        Assert.assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        Join message1 = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        Join message2 = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel"), SessionChannel.of("testChannel2")));
        Join message3 = new Join(SessionUID.of("test"), Set.of(SessionChannel.of("testChannel2"), SessionChannel.of("testChannel3")));

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
