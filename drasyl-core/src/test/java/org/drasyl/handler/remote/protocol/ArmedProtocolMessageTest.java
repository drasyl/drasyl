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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
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
import java.net.InetSocketAddress;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.drasyl.handler.remote.protocol.Nonce.randomNonce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
public class ArmedProtocolMessageTest {
    @Nested
    class IncrementHopCount {
        private ArmedProtocolMessage armedMessage;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            final FullReadMessage<?> fullReadMessage = ApplicationMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
            armedMessage = fullReadMessage.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(armedMessage);
        }

        @Test
        void shouldIncrementHopCount() {
            final ArmedProtocolMessage newArmedMessage = armedMessage.incrementHopCount();

            assertEquals(armedMessage.getNonce(), newArmedMessage.getNonce());
            assertEquals(armedMessage.getHopCount().increment(), newArmedMessage.getHopCount());
        }

        @Test
        void returnedMessageShouldNotRetainByteBuffer() {
            final ArmedProtocolMessage newArmedMessage = armedMessage.incrementHopCount();
            armedMessage.release();

            assertEquals(0, newArmedMessage.refCnt());
        }
    }

    @Nested
    class Disarm {
        private FullReadMessage<?> fullReadMessage;
        private ArmedProtocolMessage armedMessage;
        private SessionPair sessionPair;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            fullReadMessage = ApplicationMessage.of(HopCount.of(), true, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.copiedBuffer("Heiko", UTF_8));
            sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            armedMessage = fullReadMessage.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())); // we must invert the session pair for encryption
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(armedMessage);
        }

        @Test
        void shouldReturnDisarmedMessage() throws IOException {
            final FullReadMessage<?> fullReadMessage = armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);

            assertEquals(this.fullReadMessage, fullReadMessage);
        }

        @Test
        void shouldThrowExceptionOnWrongSessionPair() {
            assertThrows(IOException.class, () -> armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())));
        }

        @Test
        void disarmWithWrongSessionPairShouldNotCorruptMessage() throws IOException {
            assertThrows(IOException.class, () -> armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())));

            armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getRx(), sessionPair.getTx()));
        }

        @Test
        void shouldNotChangeByteBufOfArmedMessage() throws IOException {
            final FullReadMessage<?> a = armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);
            final FullReadMessage<?> b = armedMessage.disarm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);

            assertEquals(a, b);
        }

        @Test
        void shouldBeAbleToDisarmToAcknowledgementMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = AcknowledgementMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), System.currentTimeMillis());
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToApplicationMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = ApplicationMessage.of(HopCount.of(), true, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToDiscoveryMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = HelloMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), 0, System.currentTimeMillis(), ID_1.getIdentitySecretKey(), Set.of());
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToUniteMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = UniteMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_1.getIdentityPublicKey(), Set.of(new InetSocketAddress(80)));
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, SessionPair.of(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldNotModifyMessageByteBuf() throws IOException, CryptoException {
            final ApplicationMessage applicationMessage = ApplicationMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
            final ArmedProtocolMessage armedMessage = applicationMessage.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));

            final int readableBytes = armedMessage.getBytes().readableBytes();
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            armedMessage.writeTo(byteBuf);

            assertEquals(readableBytes, armedMessage.getBytes().readableBytes());

            byteBuf.release();
        }

        @Test
        void multipleCallsShouldWriteSameBytes() throws IOException, CryptoException {
            final ApplicationMessage applicationMessage = ApplicationMessage.of(HopCount.of(), false, 0, randomNonce(), ID_2.getIdentityPublicKey(), ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), Unpooled.buffer());
            final PartialReadMessage armedMessage = applicationMessage.arm(UnpooledByteBufAllocator.DEFAULT, Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));

            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            armedMessage.writeTo(byteBuf);

            final ByteBuf byteBuf2 = PooledByteBufAllocator.DEFAULT.buffer();
            armedMessage.writeTo(byteBuf2);

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
        private ByteBuf bytes;

        @Nested
        class Close {
            @Test
            void shouldReleaseBytes() {
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).close();

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
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).refCnt();

                verify(bytes).refCnt();
            }
        }

        @Nested
        class Retain {
            @Test
            void shouldBeDelegateToBytes() {
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).retain();
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).retain(1337);

                verify(bytes).retain();
                verify(bytes).retain(1337);
            }
        }

        @Nested
        class Touch {
            @Test
            void shouldBeDelegateToBytes(@Mock final Object hint) {
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).touch();
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).touch(hint);

                verify(bytes).touch();
                verify(bytes).touch(hint);
            }
        }

        @Nested
        class Release {
            @Test
            void shouldBeDelegateToBytes() {
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).release();
                ArmedProtocolMessage.of(nonce, hopCount, networkId, recipient, sender, proofOfWork, bytes).release(1337);

                verify(bytes).release();
                verify(bytes).release(1337);
            }
        }
    }
}
