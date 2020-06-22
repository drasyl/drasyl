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
import org.drasyl.peer.PeerInformation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class WelcomeMessageTest {
    private final String correspondingId = "123";
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private PeerInformation peerInformation;

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            String json = "{\"@type\":\"WelcomeMessage\",\"id\":\"4AE5CDCD8C21719F8E779F21\",\"userAgent\":\"\",\"publicKey\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\",\"peerInformation\":{\"endpoints\":[\"ws://test\"]},\"correspondingId\":\"123\"}";

            assertEquals(new WelcomeMessage(CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"), PeerInformation.of(Set.of(URI.create("ws://test"))), "123"), JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            WelcomeMessage message = new WelcomeMessage(publicKey, PeerInformation.of(), correspondingId);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", WelcomeMessage.class.getSimpleName())
                    .containsKeys("id", "userAgent", "publicKey", "peerInformation", "correspondingId");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            WelcomeMessage message1 = new WelcomeMessage(publicKey, peerInformation, correspondingId);
            WelcomeMessage message2 = new WelcomeMessage(publicKey, peerInformation, correspondingId);

            assertEquals(message1, message2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            WelcomeMessage message1 = new WelcomeMessage(publicKey, peerInformation, correspondingId);
            WelcomeMessage message2 = new WelcomeMessage(publicKey, peerInformation, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}
