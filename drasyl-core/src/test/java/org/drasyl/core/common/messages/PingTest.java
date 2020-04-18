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
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PingTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        Ping message = new Ping();

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Ping\",\"messageID\":\"" + message.getMessageID() + "\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Ping\",\"messageID\":\"77175D7235920F3BA17341D7\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(Ping.class));
    }

    @Test
    void testEquals() {
        Ping message1 = new Ping();
        Ping message2 = new Ping();

        Assert.assertTrue(message1.equals(message2));
    }

    @Test
    void testHashCode() {
        Ping message1 = new Ping();
        Ping message2 = new Ping();

        assertEquals(message1.hashCode(), message2.hashCode());
    }
}
