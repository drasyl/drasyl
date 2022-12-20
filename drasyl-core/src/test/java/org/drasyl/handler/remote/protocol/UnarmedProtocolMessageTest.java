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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.drasyl.handler.remote.protocol.Nonce.randomNonce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class UnarmedProtocolMessageTest {
    @Nested
    class IncrementHopCount {
        private UnarmedProtocolMessage unarmedMessage;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            unarmedMessage = UnarmedProtocolMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
        }

        @AfterEach
        void tearDown() {
            if (ReferenceCountUtil.refCnt(unarmedMessage) < 0) {
                ReferenceCountUtil.safeRelease(unarmedMessage);
            }
        }

        @Test
        void shouldIncrementHopCount() {
            final UnarmedProtocolMessage newUnarmedMessage = unarmedMessage.incrementHopCount();

            assertEquals(unarmedMessage.getNonce(), newUnarmedMessage.getNonce());
            assertEquals(unarmedMessage.getHopCount().increment(), newUnarmedMessage.getHopCount());
        }

        @Test
        void returnedMessageShouldNotRetainByteBuffer() {
            final UnarmedProtocolMessage newUnarmedMessage = unarmedMessage.incrementHopCount();
            unarmedMessage.release();

            assertEquals(0, newUnarmedMessage.refCnt());
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldNotModifyMessageByteBuf() throws IOException {
            final UnarmedProtocolMessage unarmedMessage = UnarmedProtocolMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());

            final int readableBytes = unarmedMessage.getBytes().readableBytes();
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            unarmedMessage.writeTo(byteBuf);

            assertEquals(readableBytes, unarmedMessage.getBytes().readableBytes());

            byteBuf.release();
        }

        @Test
        void multipleCallsShouldWriteSameBytes() throws IOException {
            final UnarmedProtocolMessage unarmedMessage = UnarmedProtocolMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());

            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            unarmedMessage.writeTo(byteBuf);

            final ByteBuf byteBuf2 = PooledByteBufAllocator.DEFAULT.buffer();
            unarmedMessage.writeTo(byteBuf2);

            assertEquals(byteBuf, byteBuf2);

            byteBuf.release();
            byteBuf2.release();
        }
    }

    @Nested
    class GetLength {
        @Test
        void shouldReturnCorrectLength() {
            final UnarmedProtocolMessage unarmedMessage = UnarmedProtocolMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
            final int length = unarmedMessage.getLength();

            final ByteBuf byteBuf = Unpooled.buffer();
            try {
                unarmedMessage.writeTo(byteBuf);

                assertEquals(byteBuf.readableBytes(), length);
            }
            finally {
                byteBuf.release();
            }
        }
    }

    @Nested
    class AutoCloseable {
        @Mock
        private Nonce nonce;
        private final int networkId = 0;
        @Mock
        private IdentityPublicKey sender;
        @Mock
        private ProofOfWork proofOfWork;
        @Mock
        private IdentityPublicKey recipient;
        @Mock
        private HopCount hopCount;
        @Mock
        private ByteBuf bytes;

        @Nested
        class Close {
            @Test
            void shouldReleaseBytes() {
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).close();

                verify(bytes).release();
            }
        }
    }

    @Nested
    class ReferenceCounted {
        @Mock
        private Nonce nonce;
        private final int networkId = 0;
        @Mock
        private IdentityPublicKey sender;
        @Mock
        private ProofOfWork proofOfWork;
        @Mock
        private IdentityPublicKey recipient;
        @Mock
        private HopCount hopCount;
        @Mock
        private ByteBuf bytes;

        @Nested
        class RefCnt {
            @Test
            void shouldBeDelegateToBytes() {
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).refCnt();

                verify(bytes).refCnt();
            }
        }

        @Nested
        class Retain {
            @Test
            void shouldBeDelegateToBytes() {
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).retain();
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).retain(1337);

                verify(bytes).retain();
                verify(bytes).retain(1337);
            }
        }

        @Nested
        class Touch {
            @Test
            void shouldBeDelegateToBytes(@Mock final Object hint) {
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).touch();
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).touch(hint);

                verify(bytes).touch();
                verify(bytes).touch(hint);
            }
        }

        @Nested
        class Release {
            @Test
            void shouldBeDelegateToBytes() {
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).release();
                UnarmedProtocolMessage.of(hopCount, false, networkId, nonce, recipient, sender, proofOfWork, bytes).release(1337);

                verify(bytes).release();
                verify(bytes).release(1337);
            }
        }
    }
}
