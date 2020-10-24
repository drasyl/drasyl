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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SuccessMessageTest {
    @Mock
    private MessageId correspondingId;
    @Mock
    private MessageId correspondingId2;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    private final int networkId = 1;

    @BeforeEach
    void setUp() {
        correspondingId = MessageId.of("412176952b5b81fd13f84a7c");
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + SuccessMessage.class.getSimpleName() + "\",\"id\":\"c78fe75d4c93bc07e916e539\",\"networkId\":1,\"sender\":\"030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3\",\"proofOfWork\":6657650,\"recipient\":\"0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d\",\"correspondingId\":\"412176952b5b81fd13f84a7c\",\"userAgent\":\"\"}";

            assertEquals(new SuccessMessage(networkId, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(6657650), CompressedPublicKey.of("0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d"), MessageId.of("412176952b5b81fd13f84a7c")), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + SuccessMessage.class.getSimpleName() + "\",\"id\":\"c78fe75d4c93bc07e916e539\",\"correspondingId\":\"412176952b5b81fd13f84a7c\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException, CryptoException {
            final SuccessMessage message = new SuccessMessage(networkId, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(6657650), CompressedPublicKey.of("0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d"), correspondingId);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", SuccessMessage.class.getSimpleName())
                    .containsKeys("id", "correspondingId", "sender", "proofOfWork", "recipient", "userAgent", "networkId");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            final SuccessMessage message1 = new SuccessMessage(networkId, sender, proofOfWork, recipient, correspondingId);
            final SuccessMessage message2 = new SuccessMessage(networkId, sender, proofOfWork, recipient, correspondingId);
            final SuccessMessage message3 = new SuccessMessage(networkId, sender, proofOfWork, recipient, correspondingId2);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final SuccessMessage message1 = new SuccessMessage(networkId, sender, proofOfWork, recipient, correspondingId);
            final SuccessMessage message2 = new SuccessMessage(networkId, sender, proofOfWork, recipient, correspondingId);
            final SuccessMessage message3 = new SuccessMessage(networkId, sender, proofOfWork, recipient, correspondingId2);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}