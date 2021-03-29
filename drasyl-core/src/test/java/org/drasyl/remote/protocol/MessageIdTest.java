/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.remote.protocol;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.remote.protocol.MessageId.isValidMessageId;
import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        void shouldCreateCorrectIdFromString() {
            assertEquals("412176952b5b81fd", MessageId.of("412176952b5b81fd").toString());
        }

        @Test
        void shouldCreateValidIdFromBytes() {
            assertEquals(MessageId.of("412176952b5b81fd"), MessageId.of(new byte[]{
                    65,
                    33,
                    118,
                    -107,
                    43,
                    91,
                    -127,
                    -3
            }));
        }

        @Test
        void shouldCreateValidIdFromLong() {
            assertEquals(MessageId.of("412176952b5b81fd"), MessageId.of(4693162669746389501L));
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

    @Nested
    class ByteArrayValue {
        @Test
        void shouldReturnCorrectBytes() {
            assertArrayEquals(new byte[]{
                    65,
                    33,
                    118,
                    -107,
                    43,
                    91,
                    -127,
                    -3
            }, MessageId.of("412176952b5b81fd").byteArrayValue());
        }
    }

    @Nested
    class LongValue {
        @Test
        void shouldReturnCorrectBytes() {
            assertEquals(4693162669746389501L, MessageId.of("412176952b5b81fd").longValue());
        }
    }
}
