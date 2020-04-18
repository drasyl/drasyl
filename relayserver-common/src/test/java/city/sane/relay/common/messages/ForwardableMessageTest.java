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

import city.sane.relay.common.models.SessionUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ForwardableMessageTest {
    SessionUID junit = SessionUID.of("junit1");
    SessionUID junit2 = SessionUID.of("junit2");

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        ForwardableMessage message = new ForwardableMessage(junit, junit2, new byte[]{ 0x00, 0x01, 0x02 });

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"ForwardableMessage\",\"messageID\":\"" + message.getMessageID() + "\",\"senderUID\":\"junit1\",\"receiverUID\":\"junit2\",\"blob\":\"AAEC\"}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"ForwardableMessage\",\"messageID\":\"5071190768411E72EEA95D57\",\"senderUID\":\"junit1\",\"receiverUID\":\"junit2\",\"blob\":\"AAEC\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ForwardableMessage.class));
    }

    @Test
    public void nullTest() {
        assertThrows(NullPointerException.class, () -> {
            new ForwardableMessage(null, junit2, new byte[] {});
        }, "ForwardableMessage requires a fromAddress");

        assertThrows(NullPointerException.class, () -> {
            new ForwardableMessage(junit, null, new byte[] {});
        }, "ForwardableMessage requires a toAddress");

        assertThrows(NullPointerException.class, () -> {
            new ForwardableMessage(null, null, null);
        }, "ForwardableMessage requires a fromAddress, a toAddress and a blob");
    }

    @Test
    public void invalidSenderTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ForwardableMessage(SessionUID.of(junit,junit2), junit2, new byte[] {});
        }, "ForwardableMessage requires a valid sender address");
    }

    @Test
    void testEquals() {
        ForwardableMessage message1 = new ForwardableMessage(junit, junit2, new byte[]{ 0x00, 0x01, 0x02 });
        ForwardableMessage message2 = new ForwardableMessage(junit, junit2, new byte[]{ 0x00, 0x01, 0x02 });
        ForwardableMessage message3 = new ForwardableMessage(junit, junit2, new byte[]{ 0x03, 0x02, 0x01 });

        assertTrue(message1.equals(message2));
        assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        ForwardableMessage message1 = new ForwardableMessage(junit, junit2, new byte[]{ 0x00, 0x01, 0x02 });
        ForwardableMessage message2 = new ForwardableMessage(junit, junit2, new byte[]{ 0x00, 0x01, 0x02 });
        ForwardableMessage message3 = new ForwardableMessage(junit, junit2, new byte[]{ 0x03, 0x02, 0x01 });

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
