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

import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class IamMessageTest {
    @Mock
    private CompressedPublicKey publicKey;
    private String correspondingId = "123";

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            String json = "{\"@type\":\"" + IamMessage.class.getSimpleName() + "\",\"id\":\"123\",\"publicKey\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"correspondingId\":\"123\"}";

            assertEquals(new IamMessage(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), "123"), JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            IamMessage message = new IamMessage(publicKey, correspondingId);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", IamMessage.class.getSimpleName())
                    .containsKeys("id", "publicKey");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            IamMessage message1 = new IamMessage(publicKey, correspondingId);
            IamMessage message2 = new IamMessage(publicKey, correspondingId);

            assertEquals(message1, message2);

            // Ignore toString
            message1.toString();
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            IamMessage message1 = new IamMessage(publicKey, correspondingId);
            IamMessage message2 = new IamMessage(publicKey, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}