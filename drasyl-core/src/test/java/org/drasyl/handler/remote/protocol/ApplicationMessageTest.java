/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
public class ApplicationMessageTest {
    private IdentityPublicKey sender;
    private IdentityPublicKey recipient;
    private ProofOfWork proofOfWork;

    @BeforeEach
    void setUp() {
        sender = ID_1.getIdentityPublicKey();
        proofOfWork = ID_1.getProofOfWork();
        recipient = ID_2.getIdentityPublicKey();
    }

    @Nested
    class IncrementHopCount {
        private ApplicationMessage application;

        @BeforeEach
        void setUp() {
            final ByteBuf buffer = Unpooled.buffer();
            application = ApplicationMessage.of(1, recipient, sender, proofOfWork, buffer);
        }

        @Test
        void shouldIncrementHopCount() {
            final ApplicationMessage newApplication = application.incrementHopCount();

            assertEquals(application.getNonce(), newApplication.getNonce());
            assertEquals(application.getHopCount().increment(), newApplication.getHopCount());

            application.release();
        }

        @Test
        void returnedMessageShouldNotRetainByteBuffer() {
            final ApplicationMessage newApplication = application.incrementHopCount();
            application.release();

            assertEquals(0, newApplication.refCnt());
        }
    }

    @Nested
    class Of {
        @Test
        void shouldCreateApplicationMessage() {
            final ByteBuf buffer = Unpooled.buffer();
            final ApplicationMessage application = ApplicationMessage.of(1, recipient, sender, proofOfWork, buffer);

            assertEquals(1, application.getNetworkId());
            assertEquals(buffer, application.getPayload());
            assertTrue(application.getPayload().isWritable());
            buffer.release();
        }
    }

    @Nested
    class GetLength {
        @Test
        void shouldReturnCorrectLength() {
            final ByteBuf buffer = Unpooled.buffer();
            final ApplicationMessage application = ApplicationMessage.of(1, recipient, sender, proofOfWork, buffer);
            final int length = application.getLength();

            final ByteBuf byteBuf = Unpooled.buffer();
            try {
                application.writeTo(byteBuf);

                assertEquals(byteBuf.readableBytes(), length);
            }
            finally {
                byteBuf.release();
                buffer.release();
            }
        }
    }
}
