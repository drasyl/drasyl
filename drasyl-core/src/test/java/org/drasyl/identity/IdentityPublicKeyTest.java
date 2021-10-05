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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import test.util.IdentityTestUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityPublicKeyTest {
    private IdentityPublicKey publicKey;

    @BeforeEach
    void setUp() {
        publicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
    }

    @Nested
    class Of {
        @Test
        void shouldReturnCorrectKeys() {
            final IdentityPublicKey publicKey1 = publicKey;
            final IdentityPublicKey publicKey2 = IdentityPublicKey.of(publicKey1.getBytes());
            final IdentityPublicKey publicKey3 = IdentityPublicKey.of(publicKey2.getBytes());

            assertEquals(publicKey1, publicKey2);
            assertEquals(publicKey1, publicKey3);
            assertEquals(publicKey2, publicKey3);
            assertEquals(publicKey1.hashCode(), publicKey2.hashCode());
            assertEquals(publicKey1.hashCode(), publicKey3.hashCode());
            assertEquals(publicKey2.hashCode(), publicKey3.hashCode());
        }

        @Test
        void shouldRejectInvalidKeys() {
            assertThrows(IllegalArgumentException.class, () -> IdentityPublicKey.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> IdentityPublicKey.of(""));
        }
    }
}
