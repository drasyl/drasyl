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
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class ChunkedMessageTest {
    CompressedPublicKey sender;
    private ProofOfWork proofOfWork;
    CompressedPublicKey recipient;
    private MessageId id;
    private int contentLength;
    private String checksum;
    private final int networkId = 1;

    @BeforeEach
    void setUp() throws CryptoException {
        sender = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        proofOfWork = ProofOfWork.of(6657650);
        recipient = CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");
        id = MessageId.of("89ba3cd9efb7570eb3126d11");
        checksum = "abc";
        contentLength = 3;
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + ChunkedMessage.class.getSimpleName() + "\",\"id\":\"412176952b5b81fd13f84a7c\",\"networkId\":1,\"sender\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"proofOfWork\":6657650,\"recipient\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"payload\":\"AAEC\",\"userAgent\":\"\",\"checksum\":\"abc\",\"contentLength\":3}";

            assertEquals(new ChunkedMessage(
                    MessageId.of("412176952b5b81fd13f84a7c"),
                    networkId,
                    CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"),
                    ProofOfWork.of(6657650),
                    CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"),
                    new byte[]{
                            0x00,
                            0x01,
                            0x02
                    },
                    contentLength,
                    checksum
            ), JACKSON_READER.readValue(json, ApplicationMessage.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJsonFristChunk() throws IOException {
            final ChunkedMessage message = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, contentLength, checksum);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", ChunkedMessage.class.getSimpleName())
                    .containsKeys("id", "networkId", "recipient", "hopCount", "sender", "payload", "contentLength", "checksum", "proofOfWork", "userAgent", "hopCount");
        }

        @Test
        void shouldSerializeToCorrectJsonFollowChunk() throws IOException {
            final ChunkedMessage message = ChunkedMessage.createFollowChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", ChunkedMessage.class.getSimpleName())
                    .containsKeys("id", "networkId", "recipient", "hopCount", "sender", "payload", "proofOfWork", "userAgent");
        }

        @Test
        void shouldSerializeToCorrectJsonLastChunk() throws IOException {
            final ChunkedMessage message = ChunkedMessage.createLastChunk(id, networkId, sender, proofOfWork, recipient);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", ChunkedMessage.class.getSimpleName())
                    .containsKeys("id", "networkId", "sender", "proofOfWork", "recipient", "hopCount", "userAgent");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final ChunkedMessage message1 = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, contentLength, checksum);
            final ChunkedMessage message2 = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, contentLength, checksum);
            final ChunkedMessage message3 = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x03,
                    0x02,
                    0x01
            }, contentLength, checksum);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final ChunkedMessage message1 = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, contentLength, checksum);
            final ChunkedMessage message2 = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, contentLength, checksum);
            final ChunkedMessage message3 = ChunkedMessage.createFirstChunk(id, networkId, sender, proofOfWork, recipient, new byte[]{
                    0x03,
                    0x02,
                    0x01
            }, contentLength, checksum);

            assertEquals(message1, message2);
            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}