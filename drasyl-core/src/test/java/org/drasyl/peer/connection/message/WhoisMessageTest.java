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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class WhoisMessageTest {
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private CompressedPublicKey requester;
    @Mock
    private PeerInformation peerInformation;
    @Mock
    private PeerInformation peerInformation2;

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            String json = "{\"@type\":\"WhoisMessage\",\"id\":\"4AE5CDCD8C21719F8E779F21\",\"recipient\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"requester\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"peerInformation\":{\"endpoints\":[\"ws://test\"]}}";

            assertEquals(new WhoisMessage(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), PeerInformation.of(Set.of(URI.create("ws://test")))), JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException, CryptoException {
            WhoisMessage message = new WhoisMessage(CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), PeerInformation.of(Set.of(URI.create("ws://test"))));

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", WhoisMessage.class.getSimpleName())
                    .containsKeys("id", "recipient", "requester", "peerInformation");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            WhoisMessage message1 = new WhoisMessage(recipient, requester, peerInformation);
            WhoisMessage message2 = new WhoisMessage(recipient, requester, peerInformation);
            WhoisMessage message3 = new WhoisMessage(recipient, requester, peerInformation2);

            assertEquals(message1, message2);
            assertNotEquals(message1, message3);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            WhoisMessage message1 = new WhoisMessage(recipient, requester, peerInformation);
            WhoisMessage message2 = new WhoisMessage(recipient, requester, peerInformation);
            WhoisMessage message3 = new WhoisMessage(recipient, requester, peerInformation2);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message1.hashCode(), message3.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }

    @Nested
    class IncrementHopCount {
        @Test
        void shouldIncrementHopCountByOne() {
            WhoisMessage message = new WhoisMessage(recipient, requester, peerInformation);

            message.incrementHopCount();

            assertEquals(1, message.getHopCount());
        }
    }
}