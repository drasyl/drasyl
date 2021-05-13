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
package org.drasyl.remote.handler.crypto;

import org.drasyl.identity.KeyAgreementPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AgreementIdTest {
    @Nested
    class Create {
        @Test
        void shouldCreateValidAgreementIdFromKeys() {
            final AgreementId id = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

            assertEquals(64, id.getId().length());
            assertEquals(32, id.toBytes().length);
        }

        @Test
        void shouldCreateValidAgreementIdFromKeysInCorrectOrder() {
            final AgreementId id = AgreementId.of(
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey());

            assertEquals(64, id.getId().length());
            assertEquals(32, id.toBytes().length);
        }

        @Test
        void shouldCreateValidAgreementIdFromString() {
            final AgreementId id = AgreementId.of("888389db5a69189ceee71846ceadfbceca9cd35fe5caffe9dae2df4edef150b6");

            assertEquals(64, id.getId().length());
            assertEquals(32, id.toBytes().length);
            assertEquals("888389db5a69189ceee71846ceadfbceca9cd35fe5caffe9dae2df4edef150b6", id.getId());
        }

        @Test
        void shouldCreateValidAgreementIdFromByteArray() {
            final byte[] bytes = new byte[]{
                    -120,
                    -125,
                    -119,
                    -37,
                    90,
                    105,
                    24,
                    -100,
                    -18,
                    -25,
                    24,
                    70,
                    -50,
                    -83,
                    -5,
                    -50,
                    -54,
                    -100,
                    -45,
                    95,
                    -27,
                    -54,
                    -1,
                    -23,
                    -38,
                    -30,
                    -33,
                    78,
                    -34,
                    -15,
                    80,
                    -74
            };

            final AgreementId id = AgreementId.of(bytes);

            assertEquals(64, id.getId().length());
            assertEquals(32, id.toBytes().length);
            assertArrayEquals(bytes, id.toBytes());
        }
    }

    @Nested
    class Validation {
        @Test
        void shouldThrowExceptionOnInvalidId() {
            final String oneMoreThanMaxLength = " ".repeat(65);
            final String oneLessThanMinLength = " ".repeat(63);

            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(new byte[31]));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(new byte[33]));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(oneMoreThanMaxLength));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(oneLessThanMinLength));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(""));
        }

        @Test
        void shouldThrowExceptionOnEqualKeys() {
            final KeyAgreementPublicKey pk1 = IdentityTestUtil.ID_1.getKeyAgreementPublicKey();

            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(pk1, pk1));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEqualsOnEqualIds() {
            final AgreementId id1 = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId id2 = AgreementId.of(
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey());

            assertEquals(id1, id2);
            assertEquals(id1.getId(), id2.getId());
            assertArrayEquals(id1.toBytes(), id2.toBytes());
            assertEquals(id1.toString(), id2.toString());
            assertEquals(id1, id1);
            assertEquals(id2, id2);
        }

        @Test
        void shouldNotBeEqualsOnUnequalIds() {
            final AgreementId id1 = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId id2 = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_3.getKeyAgreementPublicKey());

            assertNotEquals(id1, id2);
            assertNotEquals(id1.getId(), id2.getId());
            assertFalse(Arrays.equals(id1.toBytes(), id2.toBytes()));
            assertNotEquals(id1.toString(), id2.toString());
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEqualsOnEqualIds() {
            final AgreementId id1 = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId id2 = AgreementId.of(
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey());

            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        void shouldNotBeEqualsOnUnequalIds() {
            final AgreementId id1 = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId id2 = AgreementId.of(
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_3.getKeyAgreementPublicKey());

            assertNotEquals(id1.hashCode(), id2.hashCode());
        }
    }
}
