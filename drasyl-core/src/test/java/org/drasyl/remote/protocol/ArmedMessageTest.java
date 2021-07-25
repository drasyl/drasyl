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

import com.google.protobuf.ByteString;
import com.goterl.lazysodium.utils.SessionPair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.crypto.Crypto;
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
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
public class ArmedMessageTest {
    @Nested
    class IncrementHopCount {
        private ArmedMessage armedMessage;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> fullReadMessage = ApplicationMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, String.class.getName(), ByteString.EMPTY);
            armedMessage = fullReadMessage.arm(Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(armedMessage);
        }

        @Test
        void shouldIncrementHopCount() {
            final ArmedMessage newArmedMessage = armedMessage.incrementHopCount();

            assertEquals(armedMessage.getNonce(), newArmedMessage.getNonce());
            assertEquals(armedMessage.getHopCount().increment(), newArmedMessage.getHopCount());
        }

        @Test
        void returnedMessageShouldRetainByteBuffer() {
            final ArmedMessage newArmedMessage = armedMessage.incrementHopCount();
            armedMessage.release();

            assertEquals(1, newArmedMessage.refCnt());
        }
    }

    @Nested
    class Disarm {
        private FullReadMessage<?> fullReadMessage;
        private ArmedMessage armedMessage;
        private SessionPair sessionPair;

        @BeforeEach
        void setUp() throws IOException, CryptoException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            fullReadMessage = ApplicationMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, String.class.getName(), ByteString.copyFromUtf8("Heiko"));
            sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            armedMessage = fullReadMessage.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())); // we must invert the session pair for encryption
        }

        @AfterEach
        void tearDown() {
            ReferenceCountUtil.safeRelease(armedMessage);
        }

        @Test
        void shouldReturnDisarmedMessage() throws IOException {
            final FullReadMessage<?> fullReadMessage = armedMessage.disarm(Crypto.INSTANCE, sessionPair);

            assertEquals(this.fullReadMessage, fullReadMessage);
        }

        @Test
        void shouldThrowExceptionOnWrongSessionPair() {
            assertThrows(IOException.class, () -> armedMessage.disarm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())));
        }

        @Test
        void disarmWithWrongSessionPairShouldNotCorruptMessage() throws IOException {
            assertThrows(IOException.class, () -> armedMessage.disarm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())));

            armedMessage.disarm(Crypto.INSTANCE, new SessionPair(sessionPair.getRx(), sessionPair.getTx()));
        }

        @Test
        void shouldNotChangeByteBufOfArmedMessage() throws IOException {
            final FullReadMessage<?> a = armedMessage.disarm(Crypto.INSTANCE, sessionPair);
            final FullReadMessage<?> b = armedMessage.disarm(Crypto.INSTANCE, sessionPair);

            assertEquals(a, b);
        }

        @Test
        void shouldBeAbleToDisarmToAcknowledgementMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = AcknowledgementMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()), randomNonce());
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToApplicationMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = ApplicationMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()), String.class.getName(), ByteString.EMPTY);
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToDiscoveryMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = DiscoveryMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()), 0);
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToKeyExchangeAcknowledgementMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = KeyExchangeAcknowledgementMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()));
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToKeyExchangeMessage() throws CryptoException, InvalidMessageFormatException {
            final FullReadMessage<?> message = KeyExchangeMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()), ID_1.getKeyAgreementPublicKey());
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }

        @Test
        void shouldBeAbleToDisarmToUniteMessage() throws CryptoException, InvalidMessageFormatException, UnknownHostException {
            final FullReadMessage<?> message = UniteMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()), ID_1.getIdentityPublicKey(), InetAddress.getByName("127.0.0.1"), 80);
            final SessionPair sessionPair = Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey());
            final FullReadMessage<?> disarmedMessage = message.arm(Crypto.INSTANCE, new SessionPair(sessionPair.getTx(), sessionPair.getRx())).disarmAndRelease(Crypto.INSTANCE, sessionPair);

            assertEquals(message, disarmedMessage);
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldNotModifyMessageByteBuf() throws IOException, CryptoException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            final ApplicationMessage applicationMessage = ApplicationMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, String.class.getName(), ByteString.EMPTY);
            final ArmedMessage armedMessage = applicationMessage.arm(Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));

            final int readableBytes = armedMessage.getBytes().readableBytes();
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            armedMessage.writeTo(byteBuf);

            assertEquals(readableBytes, armedMessage.getBytes().readableBytes());

            byteBuf.release();
        }

        @Test
        void multipleCallsShouldWriteSameBytes() throws IOException, CryptoException {
            final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
            final ApplicationMessage applicationMessage = ApplicationMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), agreementId, String.class.getName(), ByteString.EMPTY);
            final PartialReadMessage armedMessage = applicationMessage.arm(Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));

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
        private AgreementId agreementId;
        @Mock
        private ByteBuf bytes;

        @Nested
        class Close {
            @Test
            void shouldReleaseBytes() {
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).close();

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
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).refCnt();

                verify(bytes).refCnt();
            }
        }

        @Nested
        class Retain {
            @Test
            void shouldBeDelegateToBytes() {
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).retain();
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).retain(1337);

                verify(bytes).retain();
                verify(bytes).retain(1337);
            }
        }

        @Nested
        class Touch {
            @Test
            void shouldBeDelegateToBytes(@Mock final Object hint) {
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).touch();
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).touch(hint);

                verify(bytes).touch();
                verify(bytes).touch(hint);
            }
        }

        @Nested
        class Release {
            @Test
            void shouldBeDelegateToBytes() {
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).release();
                ArmedMessage.of(nonce, networkId, sender, proofOfWork, recipient, hopCount, agreementId, bytes).release(1337);

                verify(bytes).release();
                verify(bytes).release(1337);
            }
        }
    }
}
