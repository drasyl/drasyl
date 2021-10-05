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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentitySecretKeyTest {
    private IdentitySecretKey secretKey;

    @BeforeEach
    void setUp() {
        secretKey = IdentityTestUtil.ID_1.getIdentitySecretKey();
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnMaskedKey() {
            assertThat(secretKey.toString(), not(containsString(secretKey.toUnmaskedString())));
        }
    }

    @Nested
    class ToUnmaskedString {
        @Test
        void shouldReturnUnmaskedKey() {
            assertEquals("65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127", secretKey.toUnmaskedString());
        }
    }

    @Nested
    class Of {
        @Test
        void shouldReturnCorrectKeys() {
            final IdentitySecretKey secretKey1 = secretKey;
            final IdentitySecretKey secretKey2 = IdentitySecretKey.of(secretKey1.getBytes());
            final IdentitySecretKey secretKey3 = IdentitySecretKey.of(secretKey2.getBytes());

            assertEquals(secretKey1, secretKey2);
            assertEquals(secretKey1, secretKey3);
            assertEquals(secretKey2, secretKey3);
            assertEquals(secretKey1.hashCode(), secretKey2.hashCode());
            assertEquals(secretKey1.hashCode(), secretKey3.hashCode());
            assertEquals(secretKey2.hashCode(), secretKey3.hashCode());
        }

        @Test
        void shouldRejectInvalidKeys() {
            assertThrows(IllegalArgumentException.class, () -> IdentitySecretKey.of(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> IdentitySecretKey.of(""));
        }
    }
}
