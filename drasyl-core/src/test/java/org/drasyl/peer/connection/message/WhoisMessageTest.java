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
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class WhoisMessageTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private PeerInformation peerInformation;
    @Mock
    private PeerInformation peerInformation2;
    private final int networkId = 1;

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"WhoisMessage\",\"id\":\"c78fe75d4c93bc07e916e539\",\"networkId\":1,\"recipient\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"sender\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"proofOfWork\":6657650,\"peerInformation\":{\"endpoints\":[\"ws://test#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"]},\"userAgent\":\"\"}";

            assertEquals(new WhoisMessage(networkId, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(6657650), CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), PeerInformation.of(Set.of(Endpoint.of("ws://test#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")))), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"WhoisMessage\",\"id\":\"c78fe75d4c93bc07e916e539\",\"sender\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"peerInformation\":{\"endpoints\":[\"ws://test\"]}}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException, CryptoException {
            final WhoisMessage message = new WhoisMessage(networkId, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(6657650), CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), PeerInformation.of(Set.of(Endpoint.of("ws://test#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))));

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", WhoisMessage.class.getSimpleName())
                    .containsKeys("id", "recipient", "sender", "peerInformation", "userAgent", "proofOfWork", "networkId");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            final WhoisMessage message1 = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation);
            final WhoisMessage message2 = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation);
            final WhoisMessage message3 = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation2);

            assertEquals(message1, message2);
            assertNotEquals(message1, message3);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final WhoisMessage message1 = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation);
            final WhoisMessage message2 = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation);
            final WhoisMessage message3 = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation2);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message1.hashCode(), message3.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }

    @Nested
    class IncrementHopCount {
        @Test
        void shouldIncrementHopCountByOne() {
            final WhoisMessage message = new WhoisMessage(networkId, sender, proofOfWork, recipient, peerInformation);

            message.incrementHopCount();

            assertEquals(1, message.getHopCount());
        }
    }
}