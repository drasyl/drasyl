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

import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
public class HelloMessageTest {
    private IdentityPublicKey sender;
    private ProofOfWork proofOfWork;
    private IdentityPublicKey recipient;
    private long time;
    private IdentitySecretKey secretKey;

    @BeforeEach
    void setUp() {
        sender = ID_1.getIdentityPublicKey();
        proofOfWork = ID_1.getProofOfWork();
        recipient = ID_2.getIdentityPublicKey();
        time = System.currentTimeMillis();
        secretKey = ID_1.getIdentitySecretKey();
    }

    @Nested
    class Of {
        @Test
        void shouldCreateSignedHelloMessageIfChildrenTimeIsPresent() {
            final HelloMessage hello = HelloMessage.of(1, recipient, sender, proofOfWork, time, 1337L, secretKey);

            assertEquals(1, hello.getNetworkId());
            assertEquals(time, hello.getTime());
            assertEquals(1337L, hello.getChildrenTime());
            assertTrue(hello.isSigned());
            assertTrue(hello.verifySignature());
        }

        @Test
        void shouldCreateUnsignedHelloMessageIfChildrenTimeIsNotPresent() {
            final HelloMessage hello = HelloMessage.of(1, recipient, sender, proofOfWork, time, 0, secretKey);

            assertEquals(1, hello.getNetworkId());
            assertEquals(time, hello.getTime());
            assertEquals(0, hello.getChildrenTime());
            assertFalse(hello.isSigned());
            assertFalse(hello.verifySignature());
        }
    }
}
