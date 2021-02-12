/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.remote.protocol;

import org.drasyl.crypto.Crypto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.drasyl.remote.protocol.MessageId.MESSAGE_ID_LENGTH;
import static org.drasyl.remote.protocol.MessageId.isValidMessageId;
import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MessageIdTest {
    @Nested
    class Of {
        @Test
        void shouldThrowExceptionOnInvalidId() {
            assertThrows(IllegalArgumentException.class, () -> MessageId.of("412176952b"));
        }

        @Test
        void shouldCreateObjectOnValidId() {
            assertNotNull(MessageId.of("412176952b5b81fd"));
        }
    }

    @Nested
    class Equals {
        @SuppressWarnings("java:S2701")
        @Test
        void shouldRecognizeEqualPairs() {
            final MessageId idA = MessageId.of("412176952b5b81fd");
            final MessageId idB = MessageId.of("412176952b5b81fd");
            final MessageId idC = MessageId.of("78c36c82b8d11c72");

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
            final MessageId idA = MessageId.of("412176952b5b81fd");
            final MessageId idB = MessageId.of("412176952b5b81fd");
            final MessageId idC = MessageId.of("78c36c82b8d11c72");

            assertEquals(idA.hashCode(), idB.hashCode());
            assertNotEquals(idA.hashCode(), idC.hashCode());
            assertNotEquals(idB.hashCode(), idC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = MessageId.of("412176952b5b81fd").toString();

            assertEquals("412176952b5b81fd", string);
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
        void shouldReturnFalseForIdWithWrongLength() {
            assertFalse(isValidMessageId(new byte[]{ 0, 0, 1 }));
        }

        @Test
        void shouldReturnTrueForValidString() {
            assertTrue(isValidMessageId(MessageId.of("f3d0aee7962de47a").byteArrayValue()));
        }
    }
}
