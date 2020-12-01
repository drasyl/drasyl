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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AcknowledgementMessageTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    private final MessageId correspondingId = MessageId.of("412176952b5b81fd13f84a7c");

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + AcknowledgementMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"userAgent\":\"\",\"networkId\":1337,\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"proofOfWork\":3556154,\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"correspondingId\":\"QSF2lStbgf0T+Ep8\"}";

            assertEquals(new AcknowledgementMessage(
                    1337,
                    CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"),
                    ProofOfWork.of(3556154),
                    CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"),
                    MessageId.of("412176952b5b81fd13f84a7c")
            ), JACKSON_READER.readValue(json, RemoteMessage.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + AcknowledgementMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"userAgent\":\"\",\"correspondingId\":\"QSF2lStbgf0T+Ep8\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, RemoteMessage.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final AcknowledgementMessage message = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", AcknowledgementMessage.class.getSimpleName())
                    .containsKeys("id", "userAgent", "networkId", "sender", "proofOfWork", "recipient", "correspondingId", "hopCount");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            final AcknowledgementMessage message1 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);
            final AcknowledgementMessage message2 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);

            assertEquals(message1, message2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final AcknowledgementMessage message1 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);
            final AcknowledgementMessage message2 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}