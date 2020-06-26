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

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WhoAreYouMessageTest {
    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            String json = "{\"@type\":\"" + WhoAreYouMessage.class.getSimpleName() + "\",\"id\":\"77175D7235920F3BA17341D7\"}";

            assertEquals(new WhoAreYouMessage(), JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            WhoAreYouMessage message = new WhoAreYouMessage();

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", WhoAreYouMessage.class.getSimpleName())
                    .containsKeys("id");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            WhoAreYouMessage message1 = new WhoAreYouMessage();
            WhoAreYouMessage message2 = new WhoAreYouMessage();

            assertEquals(message1, message2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            WhoAreYouMessage message1 = new WhoAreYouMessage();
            WhoAreYouMessage message2 = new WhoAreYouMessage();

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}