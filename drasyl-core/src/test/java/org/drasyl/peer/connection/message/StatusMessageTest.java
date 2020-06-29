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

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_FORBIDDEN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StatusMessageTest {
    private String correspondingId;

    @BeforeEach
    void setUp() {
        correspondingId = "123";
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "{\"@type\":\"" + StatusMessage.class.getSimpleName() + "\",\"id\":\"205E5ECE2F3F1E744D951658\",\"code\":" + STATUS_OK.getNumber() + ",\"correspondingId\":123}";

            assertEquals(new StatusMessage(STATUS_OK, "123"), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            String json = "{\"@type\":\"" + StatusMessage.class.getSimpleName() + "\",\"id\":\"205E5ECE2F3F1E744D951658\",\"correspondingId\":123}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            StatusMessage message = new StatusMessage(STATUS_OK, correspondingId);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", StatusMessage.class.getSimpleName())
                    .containsKeys("id", "correspondingId", "code");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            StatusMessage message1 = new StatusMessage(STATUS_OK, correspondingId);
            StatusMessage message2 = new StatusMessage(STATUS_OK.getNumber(), correspondingId);
            StatusMessage message3 = new StatusMessage(STATUS_FORBIDDEN, correspondingId);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            StatusMessage message1 = new StatusMessage(STATUS_OK, correspondingId);
            StatusMessage message2 = new StatusMessage(STATUS_OK.getNumber(), correspondingId);
            StatusMessage message3 = new StatusMessage(STATUS_FORBIDDEN, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}
