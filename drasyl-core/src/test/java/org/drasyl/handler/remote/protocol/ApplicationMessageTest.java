/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
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
    private static final byte[] NONCE = new byte[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23
    };
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

            final ByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
            ByteBuf byteBuf = null;
            try {
                byteBuf = application.encodeMessage(alloc);

                assertEquals(byteBuf.readableBytes(), length);
            }
            finally {
                if (byteBuf != null) {
                    byteBuf.release();
                }
                buffer.release();
            }
        }
    }

    @Nested
    class EncodeMessage {
        @Test
        void shouldWriteCorrectBytes() {
            final ByteBuf payload = Unpooled.buffer(10).writerIndex(10);
            final ApplicationMessage application = ApplicationMessage.of(HopCount.of(), false, 1, Nonce.of(NONCE), recipient, sender, proofOfWork, payload);

            final ByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
            ByteBuf out = null;
            try {
                out = application.encodeMessage(alloc);

                assertEquals(Unpooled.wrappedBuffer(new byte[]{
                        30,
                        63,
                        80,
                        1,
                        0,
                        0,
                        0,
                        0,
                        1,
                        0,
                        1,
                        2,
                        3,
                        4,
                        5,
                        6,
                        7,
                        8,
                        9,
                        10,
                        11,
                        12,
                        13,
                        14,
                        15,
                        16,
                        17,
                        18,
                        19,
                        20,
                        21,
                        22,
                        23,
                        98,
                        45,
                        -122,
                        10,
                        35,
                        81,
                        123,
                        14,
                        32,
                        -27,
                        -99,
                        -118,
                        72,
                        29,
                        -76,
                        -38,
                        44,
                        -119,
                        100,
                        -100,
                        -105,
                        -99,
                        115,
                        24,
                        -68,
                        78,
                        -15,
                        -104,
                        40,
                        -12,
                        102,
                        62,
                        24,
                        -51,
                        -78,
                        -126,
                        -66,
                        -115,
                        18,
                        -109,
                        -11,
                        4,
                        12,
                        -42,
                        32,
                        -87,
                        26,
                        -54,
                        -122,
                        -92,
                        117,
                        104,
                        46,
                        77,
                        -36,
                        57,
                        125,
                        -22,
                        -66,
                        48,
                        10,
                        -83,
                        -111,
                        39,
                        -125,
                        -34,
                        18,
                        -99,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                }), out);
            }
            finally {
                out.release();
                payload.release();
            }
        }
    }
}
