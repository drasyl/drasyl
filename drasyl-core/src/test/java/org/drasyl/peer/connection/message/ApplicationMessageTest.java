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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ApplicationMessageTest {
    CompressedPublicKey sender;
    ProofOfWork proofOfWork;
    CompressedPublicKey recipient;
    private MessageId id;
    private short hopCount;
    private final int networkId = 1;

    @BeforeEach
    void setUp() throws CryptoException {
        sender = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        proofOfWork = ProofOfWork.of(6657650);
        recipient = CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");
        id = MessageId.of("89ba3cd9efb7570eb3126d11");
        hopCount = 64;
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + ApplicationMessage.class.getSimpleName() + "\",\"id\":\"412176952b5b81fd13f84a7c\",\"networkId\":1,\"sender\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"proofOfWork\":6657650,\"recipient\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"headers\":{\"clazz\":\"[B\"},\"payload\":\"AAEC\",\"userAgent\":\"\"}";

            assertEquals(new ApplicationMessage(
                    networkId,
                    CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"),
                    ProofOfWork.of(6657650),
                    CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"),
                    Map.of("clazz", byte[].class.getName()), new byte[]{
                    0x00,
                    0x01,
                    0x02
            }), JACKSON_READER.readValue(json, ApplicationMessage.class));
        }

        @Test
        void shouldDeserializeJsonWithoutHeadersToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + ApplicationMessage.class.getSimpleName() + "\",\"id\":\"412176952b5b81fd13f84a7c\",\"networkId\":1,\"sender\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"proofOfWork\":6657650,\"recipient\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"payload\":\"AAEC\",\"userAgent\":\"\"}";

            assertEquals(new ApplicationMessage(networkId, CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"), ProofOfWork.of(6657650), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), new byte[]{
                    0x00,
                    0x01,
                    0x02
            }), JACKSON_READER.readValue(json, ApplicationMessage.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + ApplicationMessage.class.getSimpleName() + "\",\"id\":\"412176952b5b81fd13f84a7c\",\"recipient\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"payload\":\"AAEC\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final ApplicationMessage message = new ApplicationMessage(networkId, sender, proofOfWork, recipient, Map.of("clazz", byte[].class.getName()), new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, (short) 64);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", ApplicationMessage.class.getSimpleName())
                    .containsKeys("id", "networkId", "recipient", "hopCount", "sender", "headers", "payload", "proofOfWork", "userAgent");
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new ApplicationMessage(networkId, null, proofOfWork, recipient, new byte[]{}), "Message requires a sender");

            assertThrows(NullPointerException.class, () -> new ApplicationMessage(networkId, sender, proofOfWork, null, new byte[]{}), "Message requires a recipient");

            assertThrows(NullPointerException.class, () -> new ApplicationMessage(networkId, sender, proofOfWork, recipient, null), "Message requires a payload");

            assertThrows(NullPointerException.class, () -> new ApplicationMessage(networkId, null, proofOfWork, null, null), "Message requires a sender, a recipient and a payload");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final ApplicationMessage message1 = new ApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final ApplicationMessage message2 = new ApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final ApplicationMessage message3 = new ApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x03,
                    0x02,
                    0x01
            });

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final ApplicationMessage message1 = new ApplicationMessage(id, networkId, sender, proofOfWork, recipient, hopCount, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final ApplicationMessage message2 = new ApplicationMessage(id, networkId, sender, proofOfWork, recipient, hopCount, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final ApplicationMessage message3 = new ApplicationMessage(id, networkId, sender, proofOfWork, recipient, hopCount, new byte[]{
                    0x03,
                    0x02,
                    0x01
            });

            assertEquals(message1.hashCode(), message2.hashCode());
            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }

    @Nested
    class IncrementHopCount {
        @Test
        void shouldIncrementHopCountByOne() {
            final ApplicationMessage message = new ApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{});

            message.incrementHopCount();

            assertEquals(1, message.getHopCount());
        }
    }
}