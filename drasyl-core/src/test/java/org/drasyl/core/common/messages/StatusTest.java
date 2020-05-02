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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class StatusTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void equalsTest() {
        Status s1 = Status.OK;
        Status s2 = new Status(200);

        assertEquals(s1, s2);
        assertEquals(Status.OK, s1);
        assertEquals(s2, s2);
        assertEquals(200, s1.getStatus());
        assertEquals(200, s2.getStatus());
        assertEquals(s1.hashCode(), s2.hashCode());

        assertNotEquals(Status.INTERNAL_SERVER_ERROR, s1);
        assertNotEquals(Status.INTERNAL_SERVER_ERROR, s2);
        assertNotEquals(Status.INTERNAL_SERVER_ERROR.getStatus(), s1.getStatus());
        assertNotEquals(Status.INTERNAL_SERVER_ERROR.getStatus(), s2.getStatus());
        assertNotEquals("200", s1);
        assertNotEquals(null, s1);

        // Ignore toString() in the coverage report
        s1.toString();
    }

    @Test
    public void outOfRangeTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new Status(1000);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new Status(-1);
        });
    }

    @Test
    public void toJson() throws JsonProcessingException {
        Status message = Status.OK;

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Status\",\"messageID\":\"" + message.getMessageID() + "\",\"status\":200,\"signature\":null}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Status\",\"messageID\":\"205E5ECE2F3F1E744D951658\",\"status\":200,\"signature\":null}";

        assertThat(JSON_MAPPER.readValue(json, IMessage.class), instanceOf(Status.class));
    }

    @Test
    void testEquals() {
        Status message1 = Status.OK;
        Status message2 = Status.OK;
        Status message3 = Status.NOT_FOUND;

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        Status message1 = Status.OK;
        Status message2 = Status.OK;
        Status message3 = Status.NOT_FOUND;

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
