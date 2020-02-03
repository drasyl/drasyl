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

import org.drasyl.all.messages.ForwardableMessage;
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

public class ForwardableP2PMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        ForwardableMessage forwardableMessage = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit2"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message = new ForwardableP2PMessage(forwardableMessage);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"ForwardableP2PMessage\",\"messageID\":\"" + message.getMessageID() + "\",\"message\":{\"type\":\"ForwardableMessage\",\"messageID\":\"" + forwardableMessage.getMessageID() + "\",\"senderUID\":\"junit\",\"receiverUID\":\"junit2\",\"blob\":\"AAEC\"}}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"ForwardableP2PMessage\",\"messageID\":\"77175D7235920F3BA17341D7\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ForwardableP2PMessage.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new ForwardableP2PMessage(null);
        }, "ForwardableP2PMessage requires a ForwardableMessage");
    }

    @Test
    void testEquals() {
        ForwardableMessage forwardableMessage1 = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit2"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message1 = new ForwardableP2PMessage(forwardableMessage1);

        ForwardableMessage forwardableMessage2 = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit2"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message2 = new ForwardableP2PMessage(forwardableMessage2);

        ForwardableMessage forwardableMessage3 = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit3"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message3 = new ForwardableP2PMessage(forwardableMessage3);

        assertTrue(message1.equals(message2));
        assertFalse(message2.equals(message3));
    }

    @Test
    void testHashCode() {
        ForwardableMessage forwardableMessage1 = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit2"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message1 = new ForwardableP2PMessage(forwardableMessage1);

        ForwardableMessage forwardableMessage2 = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit2"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message2 = new ForwardableP2PMessage(forwardableMessage2);

        ForwardableMessage forwardableMessage3 = new ForwardableMessage(SessionUID.of("junit"), SessionUID.of("junit3"), new byte[]{0x00, 0x01, 0x02});
        ForwardableP2PMessage message3 = new ForwardableP2PMessage(forwardableMessage3);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
