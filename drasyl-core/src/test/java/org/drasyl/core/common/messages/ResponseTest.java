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

package org.drasyl.core.common.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class ResponseTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        Leave message = new Leave();
        var response = new Response<>(message, message.getMessageID());

        assertThatJson(JSON_MAPPER.writeValueAsString(response))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Response\",\"messageID\":\"" + response.getMessageID() + "\",\"message\":{\"type\":\"Leave\",\"messageID\":\"" + message.getMessageID() + "\"},\"msgID\":\"" + response.getMsgID() + "\"}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Response\",\"messageID\":\"0EC61D0CDB5ED12197999ECE\"," +
                "\"message\":{\"type\":\"Leave\",\"messageID\":\"D2EDF9A065F2AED969EA37CA\"},\"msgID\":\"D2EDF9A065F2AED969EA37CA\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(Response.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new Response<>(null, "bla");
        }, "Response requires a message");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new Response<>(Status.OK, null);
        }, "Response requires a msgID");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new Response<>(null, null);
        }, "Response requires a message and a msgID");
    }

    @Test
    void testEquals() {
        Leave message1 = new Leave();
        var response1 = new Response<>(message1, message1.getMessageID());
        var response2 = new Response<>(message1, message1.getMessageID());
        Leave message3 = new Leave();
        var response3 = new Response<>(message3, message3.getMessageID());

        Assert.assertTrue(response1.equals(response2));
        Assert.assertFalse(response2.equals(response3));
    }

    @Test
    void testHashCode() {
        Leave message1 = new Leave();
        var response1 = new Response<>(message1, message1.getMessageID());
        var response2 = new Response<>(message1, message1.getMessageID());
        Leave message3 = new Leave();
        var response3 = new Response<>(message3, message3.getMessageID());

        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response2.hashCode(), response3.hashCode());
    }
}
