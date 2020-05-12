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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.core.common.message.StatusMessage.Code.STATUS_OK;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StatusMessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private String correspondingId;

    @BeforeEach
    void setUp() {
        correspondingId = "correspondingId";
    }

    @Test
    public void toJson() throws JsonProcessingException {
        StatusMessage message = new StatusMessage(STATUS_OK, correspondingId);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"@type\":\"StatusMessage\",\"id\":\"" + message.getId() + "\",\"correspondingId\":\"correspondingId\",\"code\":200}");
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"@type\":\"StatusMessage\",\"id\":\"205E5ECE2F3F1E744D951658\",\"code\":200}";

        assertThat(JSON_MAPPER.readValue(json, Message.class), instanceOf(StatusMessage.class));
    }

    @Test
    void testEquals() {
        StatusMessage message1 = new StatusMessage(STATUS_OK, correspondingId);
        StatusMessage message2 = new StatusMessage(STATUS_OK.getNumber(), correspondingId);
        StatusMessage message3 = new StatusMessage(STATUS_FORBIDDEN, correspondingId);

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        StatusMessage message1 = new StatusMessage(STATUS_OK, correspondingId);
        StatusMessage message2 = new StatusMessage(STATUS_OK.getNumber(), correspondingId);
        StatusMessage message3 = new StatusMessage(STATUS_FORBIDDEN, correspondingId);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
