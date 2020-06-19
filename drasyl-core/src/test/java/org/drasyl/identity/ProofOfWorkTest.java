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
package org.drasyl.identity;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProofOfWorkTest {
    @Nested
    class Equals {
        @Test
        void shouldReturnTrueOnSameProof() {
            ProofOfWork proof1 = ProofOfWork.of(1);
            ProofOfWork proof2 = ProofOfWork.of(1);

            assertEquals(proof1, proof2);
            assertEquals(proof1.hashCode(), proof2.hashCode());
            assertEquals(proof1.getNonce(), proof2.getNonce());
        }
    }

    @Nested
    class Of {
        @Test
        void shouldThrowExceptionOnNegativeNonce() {
            assertThrows(IllegalArgumentException.class, () -> ProofOfWork.of(-1));
        }

        @Test
        void shouldGenerateCorrectProof() throws CryptoException {
            short difficulty = 1;
            CompressedPublicKey publicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
            ProofOfWork proof1 = ProofOfWork.of(publicKey, difficulty);
            ProofOfWork proof2 = ProofOfWork.of(publicKey, difficulty);

            assertTrue(proof1.isValid(publicKey, difficulty));
            assertTrue(proof2.isValid(publicKey, difficulty));
            assertEquals(proof1, proof2);
        }
    }

    @Nested
    class IncNonce {
        @Test
        void shouldIncNonce() {
            ProofOfWork proof = ProofOfWork.of(1);
            assertEquals(1, proof.getNonce());

            proof.incNonce();
            assertEquals(2, proof.getNonce());
        }
    }
}