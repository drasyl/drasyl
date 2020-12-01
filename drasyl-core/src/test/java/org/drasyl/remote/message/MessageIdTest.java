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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.remote.message.MessageId.isValidMessageId;
import static org.drasyl.remote.message.MessageId.randomMessageId;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MessageIdTest {
    @Nested
    class Constructor {
        @Test
        void shouldValidateGivenId() {
            assertThrows(IllegalArgumentException.class, () -> MessageId.of("xyz"));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldRecognizeEqualPairs() {
            System.out.println();
            final MessageId idA = MessageId.of("412176952b5b81fd13f84a7c");
            final MessageId idB = MessageId.of("412176952b5b81fd13f84a7c");
            final MessageId idC = MessageId.of("78c36c82b8d11c7217a011b3");

            assertEquals(idA, idA);
            assertEquals(idA, idB);
            assertEquals(idB, idA);
            assertNotEquals(null, idA);
            assertNotEquals(idA, idC);
            assertNotEquals(idC, idA);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldRecognizeEqualPairs() {
            final MessageId idA = MessageId.of("412176952b5b81fd13f84a7c");
            final MessageId idB = MessageId.of("412176952b5b81fd13f84a7c");
            final MessageId idC = MessageId.of("78c36c82b8d11c7217a011b3");

            assertEquals(idA.hashCode(), idB.hashCode());
            assertNotEquals(idA.hashCode(), idC.hashCode());
            assertNotEquals(idB.hashCode(), idC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = MessageId.of("412176952b5b81fd13f84a7c").toString();

            assertEquals("412176952b5b81fd13f84a7c", string);
        }
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "\"QSF2lStbgf0T+Ep8\"";

            assertEquals(
                    MessageId.of("412176952b5b81fd13f84a7c"),
                    JACKSON_READER.readValue(json, MessageId.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final MessageId id = MessageId.of("412176952b5b81fd13f84a7c");

            assertThatJson(JACKSON_WRITER.writeValueAsString(id))
                    .isEqualTo("\"QSF2lStbgf0T+Ep8\"");
        }
    }

    @Nested
    class RandomMessageId {
        @Test
        void shouldReturnRandomMessageId() {
            final MessageId idA = randomMessageId();
            final MessageId idB = randomMessageId();

            assertNotNull(idA);
            assertNotNull(idB);
            assertNotEquals(idA, idB);
        }
    }

    @Nested
    class IsValidMessageId {
        @Test
        void shouldReturnFalseForNullString() {
            assertFalse(isValidMessageId(null));
        }

        @Test
        void shouldReturnFalseForIdWithWrongLength() {
            assertFalse(isValidMessageId(new byte[]{ 0, 0, 1 }));
        }

        @Test
        void shouldReturnTrueForValidString() {
            assertTrue(isValidMessageId(MessageId.of("f3d0aee7962de47a849bd7b0").getId()));
        }
    }
}