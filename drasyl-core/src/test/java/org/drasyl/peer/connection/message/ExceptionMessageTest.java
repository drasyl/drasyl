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
import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_FORMAT;
import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_INTERNAL;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ExceptionMessageTest {
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + ExceptionMessage.class.getSimpleName() + "\",\"proofOfWork\":3556154,\"sender\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\",\"userAgent\":\"\",\"id\":\"89ba3cd9efb7570eb3126d11\"," +
                    "\"error\":\"" + ERROR_INTERNAL.getDescription() + "\"}";

            assertEquals(new ExceptionMessage(
                    CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"),
                    ProofOfWork.of(3556154),
                    ERROR_INTERNAL
            ), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + ExceptionMessage.class.getSimpleName() + "\",\"id\":\"89ba3cd9efb7570eb3126d11\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException, CryptoException {
            final ExceptionMessage message = new ExceptionMessage(
                    CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"),
                    ProofOfWork.of(3556154),
                    ERROR_INTERNAL
            );

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", ExceptionMessage.class.getSimpleName())
                    .containsKeys("id", "error", "proofOfWork", "sender", "userAgent");
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new ExceptionMessage(sender, proofOfWork, null), "ExceptionMessage requires an error");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentError() {
            final ExceptionMessage message1 = new ExceptionMessage(sender, proofOfWork, ERROR_INTERNAL);
            final ExceptionMessage message2 = new ExceptionMessage(sender, proofOfWork, ERROR_INTERNAL);
            final ExceptionMessage message3 = new ExceptionMessage(sender, proofOfWork, ERROR_FORMAT);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentError() {
            final ExceptionMessage message1 = new ExceptionMessage(sender, proofOfWork, ERROR_INTERNAL);
            final ExceptionMessage message2 = new ExceptionMessage(sender, proofOfWork, ERROR_INTERNAL);
            final ExceptionMessage message3 = new ExceptionMessage(sender, proofOfWork, ERROR_FORMAT);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}