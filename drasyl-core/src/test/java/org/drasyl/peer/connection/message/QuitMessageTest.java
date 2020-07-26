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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class QuitMessageTest {
    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "{\"@type\":\"" + QuitMessage.class.getSimpleName() + "\",\"id\":\"89ba3cd9efb7570eb3126d11\",\"reason\":\"Unknown reason for closing this connection.\"}";

            assertEquals(new QuitMessage(), JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            QuitMessage message = new QuitMessage();

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", QuitMessage.class.getSimpleName())
                    .containsKeys("id", "reason");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            QuitMessage message1 = new QuitMessage();
            QuitMessage message2 = new QuitMessage();

            assertEquals(message1, message2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            QuitMessage message1 = new QuitMessage();
            QuitMessage message2 = new QuitMessage();

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}
