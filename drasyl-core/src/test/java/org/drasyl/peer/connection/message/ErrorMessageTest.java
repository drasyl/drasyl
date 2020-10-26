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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_IDENTITY_COLLISION;
import static org.drasyl.peer.connection.message.ErrorMessage.Error.ERROR_OTHER_NETWORK;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ErrorMessageTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private MessageId correspondingId;
    private final int networkId = 1;

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + ErrorMessage.class.getSimpleName() + "\",\"networkId\":1,\"proofOfWork\":3556154,\"sender\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\",\"recipient\":\"025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4\",\"userAgent\":\"\",\"id\":\"89ba3cd9efb7570eb3126d11\"," +
                    "\"error\":\"" + ERROR_IDENTITY_COLLISION.getDescription() + "\",\"correspondingId\":\"412176952b5b81fd13f84a7c\"}";

            assertEquals(new ErrorMessage(
                            1,
                            CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"),
                            ProofOfWork.of(3556154),
                            CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"),
                            ERROR_IDENTITY_COLLISION,
                            MessageId.of("412176952b5b81fd13f84a7c")
                    ),
                    JACKSON_READER.readValue(json, Message.class)
            );
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + ErrorMessage.class.getSimpleName() + "\",\"id\":\"89ba3cd9efb7570eb3126d11\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException, CryptoException {
            final ErrorMessage message = new ErrorMessage(
                    1,
                    CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"),
                    ProofOfWork.of(3556154),
                    CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"),
                    ERROR_IDENTITY_COLLISION,
                    MessageId.of("412176952b5b81fd13f84a7c"));

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", ErrorMessage.class.getSimpleName())
                    .containsKeys("id", "error", "sender", "proofOfWork", "recipient", "userAgent", "correspondingId", "networkId", "hopCount");
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final MessageId id = MessageId.of("412176952b5b81fd13f84a7c");
            assertThrows(NullPointerException.class, () -> new ErrorMessage(networkId, sender, proofOfWork, recipient, null, id), "ConnectionExceptionMessage requires an error type");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentError() {
            final ErrorMessage message1 = new ErrorMessage(networkId, sender, proofOfWork, recipient, ERROR_OTHER_NETWORK, correspondingId);
            final ErrorMessage message2 = new ErrorMessage(networkId, sender, proofOfWork, recipient, ERROR_OTHER_NETWORK, correspondingId);
            final ErrorMessage message3 = new ErrorMessage(networkId, sender, proofOfWork, recipient, ERROR_IDENTITY_COLLISION, correspondingId);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentError() {
            final ErrorMessage message1 = new ErrorMessage(networkId, sender, proofOfWork, recipient, ERROR_OTHER_NETWORK, correspondingId);
            final ErrorMessage message2 = new ErrorMessage(networkId, sender, proofOfWork, recipient, ERROR_OTHER_NETWORK, correspondingId);
            final ErrorMessage message3 = new ErrorMessage(networkId, sender, proofOfWork, recipient, ERROR_IDENTITY_COLLISION, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}