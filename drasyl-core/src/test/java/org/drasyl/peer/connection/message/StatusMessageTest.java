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
    private MessageId correspondingId;

    @BeforeEach
    void setUp() {
        correspondingId = new MessageId("412176952b5b81fd13f84a7c");
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "{\"@type\":\"" + StatusMessage.class.getSimpleName() + "\",\"id\":\"c78fe75d4c93bc07e916e539\",\"code\":" + STATUS_OK.getNumber() + ",\"correspondingId\":\"412176952b5b81fd13f84a7c\"}";

            assertEquals(new StatusMessage(STATUS_OK, new MessageId("412176952b5b81fd13f84a7c")), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + StatusMessage.class.getSimpleName() + "\",\"id\":\"c78fe75d4c93bc07e916e539\",\"correspondingId\":\"412176952b5b81fd13f84a7c\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final StatusMessage message = new StatusMessage(STATUS_OK, correspondingId);

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
            final StatusMessage message1 = new StatusMessage(STATUS_OK, correspondingId);
            final StatusMessage message2 = new StatusMessage(STATUS_OK.getNumber(), correspondingId);
            final StatusMessage message3 = new StatusMessage(STATUS_FORBIDDEN, correspondingId);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final StatusMessage message1 = new StatusMessage(STATUS_OK, correspondingId);
            final StatusMessage message2 = new StatusMessage(STATUS_OK.getNumber(), correspondingId);
            final StatusMessage message3 = new StatusMessage(STATUS_FORBIDDEN, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}