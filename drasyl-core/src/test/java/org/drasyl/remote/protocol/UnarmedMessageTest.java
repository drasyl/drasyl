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
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.util.ReferenceCountUtil;
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
class UnarmedMessageTest {
    @Nested
    class IncrementHopCount {
        private UnarmedMessage unarmedMessage;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            unarmedMessage = UnarmedMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, Unpooled.buffer());
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(unarmedMessage);
        }

        @Test
        void shouldIncrementHopCount() {
            final UnarmedMessage newUnarmedMessage = unarmedMessage.incrementHopCount();

            assertEquals(unarmedMessage.getNonce(), newUnarmedMessage.getNonce());
            assertEquals(unarmedMessage.getHopCount().increment(), newUnarmedMessage.getHopCount());
        }

        @Test
        void returnedMessageShouldRetainByteBuffer() {
            final UnarmedMessage newUnarmedMessage = unarmedMessage.incrementHopCount();
            unarmedMessage.release();

            assertEquals(1, newUnarmedMessage.refCnt());
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldNotModifyMessageByteBuf() throws IOException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            final UnarmedMessage unarmedMessage = UnarmedMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, Unpooled.buffer());

            final int readableBytes = unarmedMessage.getBytes().readableBytes();
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            unarmedMessage.writeTo(byteBuf);

            assertEquals(readableBytes, unarmedMessage.getBytes().readableBytes());

            byteBuf.release();
        }

        @Test
        void multipleCallsShouldWriteSameBytes() throws IOException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            final UnarmedMessage unarmedMessage = UnarmedMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, Unpooled.buffer());

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
        private AgreementId agreementId;
        @Mock
        private ByteBuf bytes;

        @Nested
        class Close {
            @Test
            void shouldReleaseBytes() {
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).close();

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
        private AgreementId agreementId;
        @Mock
        private ByteBuf bytes;

        @Nested
        class RefCnt {
            @Test
            void shouldBeDelegateToBytes() {
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).refCnt();

                verify(bytes).refCnt();
            }
        }

        @Nested
        class Retain {
            @Test
            void shouldBeDelegateToBytes() {
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).retain();
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).retain(1337);

                verify(bytes).retain();
                verify(bytes).retain(1337);
            }
        }

        @Nested
        class Touch {
            @Test
            void shouldBeDelegateToBytes(@Mock final Object hint) {
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).touch();
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).touch(hint);

                verify(bytes).touch();
                verify(bytes).touch(hint);
            }
        }

        @Nested
        class Release {
            @Test
            void shouldBeDelegateToBytes() {
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).release();
                UnarmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).release(1337);

                verify(bytes).release();
                verify(bytes).release(1337);
            }
        }
    }
}
