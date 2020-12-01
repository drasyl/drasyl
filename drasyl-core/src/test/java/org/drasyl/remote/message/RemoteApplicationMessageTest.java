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
package org.drasyl.remote.message;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.Signature;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RemoteApplicationMessageTest {
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
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "{\"@type\":\"" + RemoteApplicationMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"networkId\":1,\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"proofOfWork\":6657650,\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"type\":\"[B\",\"payload\":\"AAEC\",\"userAgent\":\"\"}";

            assertEquals(new RemoteApplicationMessage(
                    networkId,
                    sender,
                    ProofOfWork.of(6657650),
                    recipient,
                    new byte[]{
                            0x00,
                            0x01,
                            0x02
                    }), JACKSON_READER.readValue(json, RemoteApplicationMessage.class));
        }

        @Test
        void shouldDeserializeJsonWithoutHeadersToCorrectObject() throws IOException {
            final String json = "{\"@type\":\"" + RemoteApplicationMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"networkId\":1,\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"proofOfWork\":6657650,\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"payload\":\"AAEC\",\"userAgent\":\"\"}";

            assertEquals(new RemoteApplicationMessage(networkId, sender, ProofOfWork.of(6657650), recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            }), JACKSON_READER.readValue(json, RemoteApplicationMessage.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + RemoteApplicationMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"payload\":\"AAEC\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, RemoteMessage.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final RemoteApplicationMessage message = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, byte[].class.getName(), new byte[]{
                    0x00,
                    0x01,
                    0x02
            }, (short) 64, new Signature(new byte[]{}));

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", RemoteApplicationMessage.class.getSimpleName())
                    .containsKeys("id", "networkId", "recipient", "hopCount", "sender", "type", "payload", "proofOfWork", "userAgent", "signature");
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, null, proofOfWork, recipient, new byte[]{}), "Message requires a sender");

            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, sender, proofOfWork, null, new byte[]{}), "Message requires a recipient");

            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, null), "Message requires a payload");

            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, null, proofOfWork, null, null), "Message requires a sender, a recipient and a payload");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final RemoteApplicationMessage message1 = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message2 = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message3 = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
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
            final RemoteApplicationMessage message1 = new RemoteApplicationMessage(id, networkId, sender, proofOfWork, recipient, hopCount, new Signature(new byte[]{}), new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message2 = new RemoteApplicationMessage(id, networkId, sender, proofOfWork, recipient, hopCount, new Signature(new byte[]{}), new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message3 = new RemoteApplicationMessage(id, networkId, sender, proofOfWork, recipient, hopCount, new Signature(new byte[]{}), new byte[]{
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
            final RemoteApplicationMessage message = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{});

            message.incrementHopCount();

            assertEquals(1, message.getHopCount());
        }
    }
}