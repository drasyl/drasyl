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
package org.drasyl.node.handler.crypto;

import org.drasyl.identity.KeyAgreementPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.util.Arrays;

import static org.drasyl.node.handler.crypto.AgreementId.ID_LENGTH;
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

            assertEquals(ID_LENGTH, id.getId().size());
            assertEquals(ID_LENGTH, id.toBytes().length);
        }

        @Test
        void shouldCreateValidAgreementIdFromKeysInCorrectOrder() {
            final AgreementId id = AgreementId.of(
                    IdentityTestUtil.ID_2.getKeyAgreementPublicKey(),
                    IdentityTestUtil.ID_1.getKeyAgreementPublicKey());

            assertEquals(ID_LENGTH, id.getId().size());
            assertEquals(ID_LENGTH, id.toBytes().length);
        }

        @Test
        void shouldCreateValidAgreementIdFromByteArray() {
            final byte[] bytes = new byte[]{
                    44, -49, -83, 71
            };

            final AgreementId id = AgreementId.of(bytes);

            assertEquals(ID_LENGTH, id.getId().size());
            assertEquals(ID_LENGTH, id.toBytes().length);
            assertArrayEquals(bytes, id.toBytes());
        }
    }

    @Nested
    class Validation {
        @Test
        void shouldThrowExceptionOnInvalidId() {
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(new byte[ID_LENGTH - 1]));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(new byte[ID_LENGTH + 1]));
            assertThrows(IllegalArgumentException.class, () -> AgreementId.of(new byte[0]));
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
