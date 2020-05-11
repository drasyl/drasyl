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
package org.drasyl.core.common.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResponseMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        LeaveMessage message = new LeaveMessage();
        var response = new ResponseMessage<>(message, message.getId());

        assertThatJson(JSON_MAPPER.writeValueAsString(response))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"ResponseMessage\",\"id\":\"" + response.getId() + "\",\"message\":{\"@type\":\"LeaveMessage\",\"id\":\"" + message.getId() + "\"},\"correspondingId\":\"" + response.getCorrespondingId() + "\"}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"@type\":\"ResponseMessage\",\"id\":\"0EC61D0CDB5ED12197999ECE\"," +
                "\"message\":{\"@type\":\"LeaveMessage\",\"id\":\"D2EDF9A065F2AED969EA37CA\"},\"correspondingId\":\"D2EDF9A065F2AED969EA37CA\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ResponseMessage.class));
    }

    @Test
    public void nullTest() {
        assertThrows(NullPointerException.class, () -> new ResponseMessage<>(null, "bla"), "Response requires a message");

        assertThrows(NullPointerException.class, () -> new ResponseMessage<>(StatusMessage.OK, null), "Response requires a msgID");

        assertThrows(NullPointerException.class, () -> new ResponseMessage<>(null, null), "Response requires a message and a msgID");
    }

    @Test
    void testEquals() {
        LeaveMessage message1 = new LeaveMessage();
        var response1 = new ResponseMessage<>(message1, message1.getId());
        var response2 = new ResponseMessage<>(message1, message1.getId());
        LeaveMessage message3 = new LeaveMessage();
        var response3 = new ResponseMessage<>(message3, message3.getId());

        assertEquals(response1, response2);
        assertNotEquals(response2, response3);
    }

    @Test
    void testHashCode() {
        LeaveMessage message1 = new LeaveMessage();
        var response1 = new ResponseMessage<>(message1, message1.getId());
        var response2 = new ResponseMessage<>(message1, message1.getId());
        LeaveMessage message3 = new LeaveMessage();
        var response3 = new ResponseMessage<>(message3, message3.getId());

        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response2.hashCode(), response3.hashCode());
    }
}
