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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PongMessageTest {
    private String correspondingId;

    @BeforeEach
    void setUp() {
        correspondingId = "correspondingId";
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "{\"@type\":\"" + PongMessage.class.getSimpleName() + "\",\"id\":\"77175D7235920F3BA17341D7\",\"correspondingId\":\"123\"}";

            assertEquals(new PongMessage("123"), JACKSON_MAPPER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            PongMessage message = new PongMessage(correspondingId);

            assertThatJson(JACKSON_MAPPER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", PongMessage.class.getSimpleName())
                    .containsKeys("id", "correspondingId");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            PongMessage message1 = new PongMessage(correspondingId);
            PongMessage message2 = new PongMessage(correspondingId);

            assertEquals(message1, message2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            PongMessage message1 = new PongMessage(correspondingId);
            PongMessage message2 = new PongMessage(correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}
