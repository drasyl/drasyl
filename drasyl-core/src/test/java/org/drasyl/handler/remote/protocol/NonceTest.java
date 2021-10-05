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
package org.drasyl.handler.remote.protocol;

import org.drasyl.util.ImmutableByteArray;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class NonceTest {
    @Nested
    class Of {
        @Test
        void shouldThrowExceptionOnInvalidNonce() {
            assertThrows(IllegalArgumentException.class, () -> Nonce.of("412176952b"));
        }

        @Test
        void shouldCreateCorrectNonceFromString() {
            assertEquals("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7", Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7").toString());
        }

        @Test
        void shouldCreateValidIdFromBytes() {
            assertEquals(Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7"), Nonce.of(new byte[]{
                    -22,
                    15,
                    40,
                    78,
                    -17,
                    21,
                    103,
                    -59,
                    5,
                    -79,
                    38,
                    103,
                    31,
                    66,
                    -109,
                    -110,
                    75,
                    -127,
                    -76,
                    -71,
                    -46,
                    10,
                    43,
                    -25
            }));
        }
    }

    @Nested
    class Equals {
        @SuppressWarnings("java:S2701")
        @Test
        void shouldRecognizeEqualPairs() {
            final Nonce idA = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
            final Nonce idB = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
            final Nonce idC = Nonce.of("d3ea81d2ef6cd41c07db3b1857f6b66055bd571d6ab0b53a");

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
            final Nonce idA = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
            final Nonce idB = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7");
            final Nonce idC = Nonce.of("d3ea81d2ef6cd41c07db3b1857f6b66055bd571d6ab0b53a");

            assertEquals(idA.hashCode(), idB.hashCode());
            assertNotEquals(idA.hashCode(), idC.hashCode());
            assertNotEquals(idB.hashCode(), idC.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnCorrectString() {
            final String string = Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7").toString();

            assertEquals("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7", string);
        }
    }

    @Nested
    class RandomNonce {
        @Test
        void shouldReturnRandomMessageId() {
            final Nonce nonceA = Nonce.randomNonce();
            final Nonce nonceB = Nonce.randomNonce();

            assertNotNull(nonceA);
            assertNotNull(nonceB);
            assertNotEquals(nonceA, nonceB);
        }
    }

    @Nested
    class IsValidNonce {
        @Test
        void shouldReturnFalseForIdWithWrongLength() {
            Assertions.assertFalse(Nonce.isValidNonce(ImmutableByteArray.of(new byte[]{
                    0,
                    0,
                    1
            })));
        }

        @Test
        void shouldReturnTrueForValidString() {
            Assertions.assertTrue(Nonce.isValidNonce(Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7").toImmutableByteArray()));
        }
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldReturnCorrectBytes() {
            assertArrayEquals(new byte[]{
                    -22,
                    15,
                    40,
                    78,
                    -17,
                    21,
                    103,
                    -59,
                    5,
                    -79,
                    38,
                    103,
                    31,
                    66,
                    -109,
                    -110,
                    75,
                    -127,
                    -76,
                    -71,
                    -46,
                    10,
                    43,
                    -25
            }, Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7").toByteArray());
        }
    }
}
