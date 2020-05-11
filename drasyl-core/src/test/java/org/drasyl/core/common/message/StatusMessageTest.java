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

public class StatusMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void equalsTest() {
        StatusMessage s1 = StatusMessage.OK;
        StatusMessage s2 = new StatusMessage(200);

        assertEquals(s1, s2);
        assertEquals(StatusMessage.OK, s1);
        assertEquals(s2, s2);
        assertEquals(200, s1.getStatus());
        assertEquals(200, s2.getStatus());
        assertEquals(s1.hashCode(), s2.hashCode());

        assertNotEquals(StatusMessage.INTERNAL_SERVER_ERROR, s1);
        assertNotEquals(StatusMessage.INTERNAL_SERVER_ERROR, s2);
        assertNotEquals(StatusMessage.INTERNAL_SERVER_ERROR.getStatus(), s1.getStatus());
        assertNotEquals(StatusMessage.INTERNAL_SERVER_ERROR.getStatus(), s2.getStatus());
        assertNotEquals("200", s1);
        assertNotEquals(null, s1);

        // Ignore toString() in the coverage report
        s1.toString();
    }

    @Test
    public void outOfRangeTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new StatusMessage(1000);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new StatusMessage(-1);
        });
    }

    @Test
    public void toJson() throws JsonProcessingException {
        StatusMessage message = StatusMessage.OK;

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"StatusMessage\",\"id\":\"" + message.getId() + "\",\"status\":200}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"@type\":\"StatusMessage\",\"id\":\"205E5ECE2F3F1E744D951658\",\"status\":200}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(StatusMessage.class));
    }

    @Test
    void testEquals() {
        StatusMessage message1 = StatusMessage.OK;
        StatusMessage message2 = StatusMessage.OK;
        StatusMessage message3 = StatusMessage.NOT_FOUND;

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        StatusMessage message1 = StatusMessage.OK;
        StatusMessage message2 = StatusMessage.OK;
        StatusMessage message3 = StatusMessage.NOT_FOUND;

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
