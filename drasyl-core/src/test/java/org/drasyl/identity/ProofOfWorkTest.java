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
package org.drasyl.identity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import test.util.IdentityTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProofOfWorkTest {
    @Nested
    class Equals {
        @Test
        void shouldReturnTrueOnSameProof() {
            final ProofOfWork proof1 = ProofOfWork.of(1);
            final ProofOfWork proof2 = ProofOfWork.of(1);

            assertEquals(proof1, proof2);
            assertEquals(proof1.hashCode(), proof2.hashCode());
            assertEquals(proof1.getNonce(), proof2.getNonce());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnNonce() {
            final ProofOfWork proof1 = ProofOfWork.of(1);

            assertEquals("1", proof1.toString());
        }
    }

    @Nested
    class GenerateProofOfWork {
        @Test
        void shouldGenerateCorrectProof() {
            final byte difficulty = 1;
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final ProofOfWork proof1 = ProofOfWork.generateProofOfWork(publicKey, difficulty);
            final ProofOfWork proof2 = ProofOfWork.generateProofOfWork(publicKey, difficulty);

            assertTrue(proof1.isValid(publicKey, difficulty));
            assertTrue(proof2.isValid(publicKey, difficulty));
            assertEquals(proof1, proof2);
        }
    }

    @Nested
    class IncNonce {
        @Test
        void shouldIncNonce() {
            final ProofOfWork proof = ProofOfWork.of(1);

            assertEquals(ProofOfWork.of(2), proof.incNonce());
        }
    }

    @Nested
    class Invalid {
        @Test
        void shouldThrowExceptionOnInvalidValue() {
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final ProofOfWork proof = ProofOfWork.generateProofOfWork(publicKey, (byte) 1);

            assertThrows(IllegalArgumentException.class, () -> proof.isValid(publicKey, (byte) 65));
            assertThrows(IllegalArgumentException.class, () -> proof.isValid(publicKey, (byte) -1));
        }
    }
}
