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

package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_FORMAT;
import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_INTERNAL;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExceptionMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void toJson() throws JsonProcessingException {
        ExceptionMessage message = new ExceptionMessage(ERROR_INTERNAL);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"" + message.getClass().getSimpleName() + "\",\"id\":\"" + message.getId() + "\",\"error\":\"Internal Error occurred.\"}");

        // Ignore toString()
        message.toString();
    }

    @Test
    void fromJson() throws IOException {
        String json = "{\"@type\":\"" + ExceptionMessage.class.getSimpleName() + "\",\"id\":\"77175D7235920F3BA17341D7\"," +
                "\"error\":\"Internal Error occurred.\"}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(ExceptionMessage.class));
    }

    @Test
    void nullTest() {
        assertThrows(NullPointerException.class, () -> new ExceptionMessage(null), "ExceptionMessage requires an error");
    }

    @Test
    void testEquals() {
        ExceptionMessage message1 = new ExceptionMessage(ERROR_INTERNAL);
        ExceptionMessage message2 = new ExceptionMessage(ERROR_INTERNAL);
        ExceptionMessage message3 = new ExceptionMessage(ERROR_FORMAT);

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        ExceptionMessage message1 = new ExceptionMessage(ERROR_INTERNAL);
        ExceptionMessage message2 = new ExceptionMessage(ERROR_INTERNAL);
        ExceptionMessage message3 = new ExceptionMessage(ERROR_FORMAT);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
