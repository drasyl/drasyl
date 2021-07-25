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
package org.drasyl.remote.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class HeadChunkMessageTest {
    @Nested
    class IncrementHopCount {
        private HeadChunkMessage chunkMessage;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            chunkMessage = HeadChunkMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), UnsignedShort.of(1), Unpooled.buffer());
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(chunkMessage);
        }

        @Test
        void shouldIncrementHopCount() {
            final HeadChunkMessage newChunkMessage = chunkMessage.incrementHopCount();

            assertEquals(chunkMessage.getNonce(), newChunkMessage.getNonce());
            assertEquals(chunkMessage.getHopCount().increment(), newChunkMessage.getHopCount());
        }

        @Test
        void returnedMessageShouldRetainByteBuffer() {
            final HeadChunkMessage newChunkMessage = chunkMessage.incrementHopCount();
            chunkMessage.release();

            assertEquals(1, newChunkMessage.refCnt());
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldNotModifyMessageByteBuf() throws IOException {
            final HeadChunkMessage chunkMessage = HeadChunkMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), UnsignedShort.of(1), Unpooled.buffer());

            final int readableBytes = chunkMessage.getBytes().readableBytes();
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            chunkMessage.writeTo(byteBuf);

            assertEquals(readableBytes, chunkMessage.getBytes().readableBytes());

            byteBuf.release();
        }

        @Test
        void multipleCallsShouldWriteSameBytes() throws IOException {
            final HeadChunkMessage chunkMessage = HeadChunkMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), UnsignedShort.of(1), Unpooled.buffer());

            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            chunkMessage.writeTo(byteBuf);

            final ByteBuf byteBuf2 = PooledByteBufAllocator.DEFAULT.buffer();
            chunkMessage.writeTo(byteBuf2);

            assertEquals(byteBuf, byteBuf2);

            byteBuf.release();
            byteBuf2.release();
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
        private final UnsignedShort totalChunks = UnsignedShort.of(1);
        @Mock
        private ByteBuf bytes;

        @Nested
        class Close {
            @Test
            void shouldReleaseBytes() {
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).close();

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
        private final UnsignedShort totalChunks = UnsignedShort.of(1);
        @Mock
        private ByteBuf bytes;

        @Nested
        class RefCnt {
            @Test
            void shouldBeDelegateToBytes() {
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).refCnt();

                verify(bytes).refCnt();
            }
        }

        @Nested
        class Retain {
            @Test
            void shouldBeDelegateToBytes() {
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).retain();
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).retain(1337);

                verify(bytes).retain();
                verify(bytes).retain(1337);
            }
        }

        @Nested
        class Touch {
            @Test
            void shouldBeDelegateToBytes(@Mock final Object hint) {
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).touch();
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).touch(hint);

                verify(bytes).touch();
                verify(bytes).touch(hint);
            }
        }

        @Nested
        class Release {
            @Test
            void shouldBeDelegateToBytes() {
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).release();
                HeadChunkMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, totalChunks, bytes).release(1337);

                verify(bytes).release();
                verify(bytes).release(1337);
            }
        }
    }
}
