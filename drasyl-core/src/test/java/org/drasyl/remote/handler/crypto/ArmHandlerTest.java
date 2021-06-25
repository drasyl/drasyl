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
package org.drasyl.remote.handler.crypto;

import com.goterl.lazysodium.utils.SessionPair;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.ConcurrentReference;
import org.drasyl.util.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArmHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private PeersManager peersManager;
    private int networkId;
    private InetSocketAddressWrapper receiveAddress;
    private SessionPair sessionPairSender;
    private SessionPair sessionPairReceiver;
    private Duration sessionExpireTime;
    private Duration sessionRetryInterval;
    private int maxAgreements;
    private int maxSessionsCount;

    @BeforeEach
    void setUp() throws CryptoException {
        networkId = -1;
        receiveAddress = new InetSocketAddressWrapper("127.0.0.1", 22527);
        sessionPairSender = Crypto.INSTANCE.generateSessionKeyPair(
                IdentityTestUtil.ID_1.getKeyAgreementKeyPair(),
                IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
        sessionPairReceiver = Crypto.INSTANCE.generateSessionKeyPair(
                IdentityTestUtil.ID_2.getKeyAgreementKeyPair(),
                IdentityTestUtil.ID_1.getKeyAgreementPublicKey());
        sessionExpireTime = Duration.ofSeconds(100000);
        sessionRetryInterval = Duration.ofSeconds(1000);
        maxAgreements = 2;
        maxSessionsCount = 5;
    }

    @Nested
    class Encryption {
        @Test
        void shouldEncryptOutgoingMessageWithRecipientAndFromMe() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body).
                        setAgreementId(agreementId);

                final RemoteEnvelope<?> encryptedMsg = RemoteEnvelope.of(msg.getOrBuildByteBuf().copy())
                        .armAndRelease(sessionPairSender);

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                final Predicate<RemoteEnvelope> correctEncryptedMessage = v -> {
                    try {
                        final RemoteEnvelope<?> copy = RemoteEnvelope.of(v.copy());
                        final RemoteEnvelope<?> disarmed = v.disarm(sessionPairReceiver);

                        if (disarmed.getPrivateHeader().getType().equals(Protocol.MessageType.APPLICATION)) {
                            final Protocol.Application app = (Protocol.Application) disarmed.getBody();
                            return Arrays.equals(body, app.getPayload().toByteArray()) && encryptedMsg.copy().equals(copy.copy());
                        }
                        else {
                            return true;
                        }
                    }
                    catch (final Exception e) {
                        return false;
                    }
                    finally {
                        v.releaseAll();
                    }
                };

                observer.awaitCount(2).assertValueCount(2);
                observer.assertValueAt(0, correctEncryptedMessage);
                observer.assertValueAt(1, correctEncryptedMessage);
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithSenderThatIsNotMe() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_3.getIdentityPublicKey(),
                        IdentityTestUtil.ID_3.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body).
                        setAgreementId(agreementId);

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithNoRecipient() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final RemoteEnvelope<?> msg = RemoteEnvelope.discovery(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork());

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithLoopback() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body);

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldEncryptOutgoingMessageWithPFSIfAvailable() throws InvalidMessageFormatException {
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final Session session = new Session(agreementId, sessionPairSender, maxAgreements, sessionExpireTime);
            final Map<IdentityPublicKey, Session> sessions = new HashMap<>();
            sessions.put(IdentityTestUtil.ID_2.getIdentityPublicKey(), session);

            session.getCurrentActiveAgreement().computeOnCondition(x -> true, y -> Agreement.builder()
                    .setAgreementId(Optional.of(agreementId))
                    .setRecipientsKeyAgreementKey(Optional.of(IdentityTestUtil.ID_2.getKeyAgreementPublicKey()))
                    .setKeyPair(IdentityTestUtil.ID_1.getKeyAgreementKeyPair())
                    .setSessionPair(Optional.of(sessionPairSender))
                    .setStaleAt(OptionalLong.of(System.currentTimeMillis() + 600_000))
                    .build());

            final ArmHandler handler = new ArmHandler(sessions, Crypto.INSTANCE, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body).
                        setAgreementId(agreementId);

                final RemoteEnvelope<?> encryptedMsg = RemoteEnvelope.of(msg.getOrBuildByteBuf().copy())
                        .armAndRelease(sessionPairSender);

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                final Predicate<RemoteEnvelope> correctEncryptedMessage = v -> {
                    try {
                        final RemoteEnvelope<?> copy = RemoteEnvelope.of(v.copy());
                        final RemoteEnvelope<?> disarmed = v.disarm(sessionPairReceiver);

                        if (disarmed.getPrivateHeader().getType().equals(Protocol.MessageType.APPLICATION)) {
                            final Protocol.Application app = (Protocol.Application) disarmed.getBody();
                            return Arrays.equals(body, app.getPayload().toByteArray()) && encryptedMsg.copy().equals(copy.copy());
                        }
                        else {
                            return true;
                        }
                    }
                    catch (final Exception e) {
                        return false;
                    }
                };

                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(correctEncryptedMessage);
                assertTrue(future.isDone());
            }
        }
    }

    @Nested
    class KeyExchange {
        @Test
        void shouldSendKeyExchangeMessageOnOutbound() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body);

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                final Predicate<RemoteEnvelope> correctEncryptedMessage = v -> {
                    try {
                        final RemoteEnvelope<?> disarmed = v.disarm(sessionPairReceiver);

                        if (!disarmed.getPrivateHeader().getType().equals(Protocol.MessageType.APPLICATION)) {
                            return disarmed.getPrivateHeader().getType().equals(Protocol.MessageType.KEY_EXCHANGE);
                        }
                        else {
                            return true;
                        }
                    }
                    catch (final Exception e) {
                        return false;
                    }
                };

                observer.awaitCount(2).assertValueCount(2);
                observer.assertValueAt(0, correctEncryptedMessage);
                observer.assertValueAt(1, correctEncryptedMessage);
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldNotSendKeyExchangeMessageOnOutboundIfMaxAgreementOptionIsZero() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, 0, sessionExpireTime, sessionRetryInterval);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body);

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                observer.awaitCount(1)
                        .assertValueCount(1);
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldNotSendKeyExchangeMessageOnInbound() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body)
                        .setAgreementId(agreementId);

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg.arm(sessionPairSender));

                observer.awaitCount(1)
                        .assertNoValues();
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldSendKeyExchangeMessageOnInboundOnUnknownAgreementId() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();
                // construct wrong agreement id
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_3.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        IdentityTestUtil.ID_2.getProofOfWork(),
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body)
                        .setAgreementId(agreementId);

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg.arm(sessionPairSender));

                final Predicate<RemoteEnvelope> correctEncryptedMessage = v -> {
                    try {
                        return v.disarmAndRelease(sessionPairReceiver).getPrivateHeader().getType().equals(Protocol.MessageType.KEY_EXCHANGE);
                    }
                    catch (final Exception e) {
                        return false;
                    }
                };

                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(correctEncryptedMessage);
                assertTrue(future.isCompletedExceptionally());
            }
        }

        @Test
        void shouldActivateAgreementOnAck() throws InvalidMessageFormatException {
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId agreementId2 = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final Session session = new Session(agreementId, sessionPairReceiver, maxAgreements, sessionExpireTime);
            final Map<IdentityPublicKey, Session> sessions = new HashMap<>();
            sessions.put(IdentityTestUtil.ID_1.getIdentityPublicKey(), session);

            session.getCurrentInactiveAgreement().computeOnCondition(x -> true, y -> Agreement.builder()
                    .setAgreementId(Optional.of(agreementId2))
                    .setRecipientsKeyAgreementKey(Optional.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey()))
                    .setKeyPair(IdentityTestUtil.ID_2.getKeyAgreementKeyPair())
                    .setSessionPair(Optional.of(sessionPairReceiver))
                    .build());

            final ArmHandler handler = new ArmHandler(sessions, Crypto.INSTANCE, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler)) {
                final TestObserver<Event> observer = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final RemoteEnvelope<?> msg = RemoteEnvelope.keyExchangeAcknowledgement(networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        agreementId2)
                        .setAgreementId(agreementId)
                        .armAndRelease(sessionPairSender);

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                future.join();
                assertTrue(future.isDone());
                assertTrue(session.getCurrentActiveAgreement().getValue().isPresent());
                assertEquals(agreementId2, session.getCurrentActiveAgreement().getValue().get().getAgreementId().get());
                assertTrue(session.getInitializedAgreements().containsKey(agreementId2));
                observer.assertValueCount(1)
                        .assertValue(v -> v instanceof PerfectForwardSecrecyEncryptionEvent);
            }
        }

        @Test
        void shouldActivateAgreementOnMessageBeforeAck() throws InvalidMessageFormatException {
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId agreementId2 = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final Session session = new Session(agreementId, sessionPairReceiver, maxAgreements, sessionExpireTime);
            final Map<IdentityPublicKey, Session> sessions = new HashMap<>();
            sessions.put(IdentityTestUtil.ID_1.getIdentityPublicKey(), session);

            session.getCurrentInactiveAgreement().computeOnCondition(x -> true, y -> Agreement.builder()
                    .setAgreementId(Optional.of(agreementId2))
                    .setRecipientsKeyAgreementKey(Optional.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey()))
                    .setKeyPair(IdentityTestUtil.ID_2.getKeyAgreementKeyPair())
                    .setSessionPair(Optional.of(sessionPairReceiver))
                    .build());

            final ArmHandler handler = new ArmHandler(sessions, Crypto.INSTANCE, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler)) {
                final TestObserver<Event> observer = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body)
                        .setAgreementId(agreementId2)
                        .armAndRelease(sessionPairSender);

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                future.join();
                assertTrue(future.isDone());
                assertTrue(session.getCurrentActiveAgreement().getValue().isPresent());
                assertEquals(agreementId2, session.getCurrentActiveAgreement().getValue().get().getAgreementId().get());
                assertTrue(session.getInitializedAgreements().containsKey(agreementId2));
                observer.assertValueCount(1)
                        .assertValue(v -> v instanceof PerfectForwardSecrecyEncryptionEvent);
            }
        }

        @Test
        void shouldResendKeyExchangeMessageOnInvalidAgreementId() throws InvalidMessageFormatException {
            // construct not existing agreementId
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();
                final TestObserver<Event> observerEvents = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final byte[] body = RandomUtil.randomBytes(10);
                final RemoteEnvelope<?> msg = RemoteEnvelope.application(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body)
                        .setAgreementId(agreementId)
                        .armAndRelease(sessionPairSender);

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                final Predicate<RemoteEnvelope> correctEncryptedMessage = v -> {
                    try {
                        return v.disarm(sessionPairSender).getPrivateHeader().getType().equals(Protocol.MessageType.KEY_EXCHANGE);
                    }
                    catch (final Exception e) {
                        return false;
                    }
                };

                assertThrows(CompletionException.class, future::join);
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(correctEncryptedMessage);
                observerEvents.assertNoValues();
            }
        }

        @Test
        void shouldInitAgreementOnValidKeyExchangeMessage() throws CryptoException, InvalidMessageFormatException {
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final Session session = new Session(agreementId, sessionPairReceiver, maxAgreements, sessionExpireTime);
            final Map<IdentityPublicKey, Session> sessions = new HashMap<>();
            sessions.put(IdentityTestUtil.ID_1.getIdentityPublicKey(), session);

            final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair = Crypto.INSTANCE.generateEphemeralKeyPair();

            final ArmHandler handler = new ArmHandler(sessions, Crypto.INSTANCE, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler)) {
                final TestObserver<Event> observerEvents = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final RemoteEnvelope<?> msg = RemoteEnvelope.keyExchange(networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        keyPair.getPublicKey())
                        .setAgreementId(agreementId)
                        .armAndRelease(sessionPairSender);

                assertFalse(session.getCurrentInactiveAgreement().getValue().isPresent());
                assertEquals(0, session.getInitializedAgreements().size());

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                future.join();
                assertTrue(future.isDone());
                assertTrue(session.getCurrentInactiveAgreement().getValue().isPresent());
                assertEquals(0, session.getInitializedAgreements().size());
                observerEvents.assertNoValues();
            }
        }

        @Test
        void shouldReplaceAndInitAgreementOnValidKeyExchangeMessage() throws InvalidMessageFormatException, CryptoException {
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final AgreementId agreementId2 = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
            final Session session = new Session(agreementId, sessionPairReceiver, maxAgreements, sessionExpireTime);
            final Map<IdentityPublicKey, Session> sessions = new HashMap<>();
            sessions.put(IdentityTestUtil.ID_1.getIdentityPublicKey(), session);

            session.getCurrentInactiveAgreement().computeOnCondition(x -> true, y -> Agreement.builder()
                    .setAgreementId(Optional.of(agreementId2))
                    .setRecipientsKeyAgreementKey(Optional.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey()))
                    .setKeyPair(IdentityTestUtil.ID_2.getKeyAgreementKeyPair())
                    .setSessionPair(Optional.of(sessionPairReceiver))
                    .build());

            final ArmHandler handler = new ArmHandler(sessions, Crypto.INSTANCE, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_2, peersManager, handler)) {
                final TestObserver<Event> observerEvents = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair = Crypto.INSTANCE.generateEphemeralKeyPair();

                final RemoteEnvelope<?> msg = RemoteEnvelope.keyExchange(networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        keyPair.getPublicKey())
                        .setAgreementId(agreementId)
                        .armAndRelease(sessionPairSender);

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                future.join();
                assertTrue(future.isDone());
                assertTrue(session.getCurrentInactiveAgreement().getValue().isPresent());
                assertEquals(keyPair.getPublicKey(), session.getCurrentInactiveAgreement().getValue().get().getRecipientsKeyAgreementKey().get());
                observerEvents.assertNoValues();
            }
        }
    }

    @Nested
    class Renew {
        @Test
        void shouldInitiateKeyExchangeIfCurrentAgreementIsRenewableOnOutbound(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                              @Mock final
                                                                              ConcurrentReference<Agreement> concurrentAgreement,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                              @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                              @Mock final Crypto crypto) throws InvalidMessageFormatException, CryptoException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(concurrentAgreement).when(session).getCurrentActiveAgreement();
                doReturn(concurrentAgreement).when(session).getCurrentInactiveAgreement();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).getValue();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).computeOnCondition(any(), any());
                doReturn(agreement).when(concurrentAgreement).computeIfAbsent(any());

                doReturn(Optional.of(mock(SessionPair.class))).when(agreement).getSessionPair();
                doReturn(Optional.of(mock(AgreementId.class))).when(agreement).getAgreementId();

                doReturn(msg).when(msg).armAndRelease(any(SessionPair.class));

                doReturn(new AtomicLong(System.currentTimeMillis() - (sessionExpireTime.toMillis() / Agreement.RENEW_DIVISOR) * 2)).when(session).getLastRenewAttemptAt();
                doReturn(true).when(agreement).isInitialized();
                doReturn(true).when(agreement).isRenewable();
                doReturn(AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey())).when(session).getLongTimeAgreementId();
                doReturn(Crypto.INSTANCE.generateSessionKeyPair(IdentityTestUtil.ID_1.getKeyAgreementKeyPair(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey())).when(session).getLongTimeAgreementPair();

                doReturn(IdentityTestUtil.ID_1.getKeyAgreementKeyPair()).when(agreement).getKeyPair();

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                future.join();
                observer.assertValue(msg);
            }
        }

        @Test
        void shouldInitiateKeyExchangeIfCurrentAgreementIsRenewableOnInbound(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                             @Mock final
                                                                             ConcurrentReference<Agreement> concurrentAgreement,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                             @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                             @Mock final Crypto crypto) throws CryptoException, InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(null).when(msg).getAgreementId();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(concurrentAgreement).when(session).getCurrentActiveAgreement();
                doReturn(concurrentAgreement).when(session).getCurrentInactiveAgreement();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).computeOnCondition(any(), any());
                doReturn(agreement).when(concurrentAgreement).computeIfAbsent(any());

                doReturn(new AtomicLong(System.currentTimeMillis() - (sessionExpireTime.toMillis() / Agreement.RENEW_DIVISOR) * 2)).when(session).getLastRenewAttemptAt();
                doReturn(true).when(agreement).isRenewable();
                doReturn(AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey())).when(session).getLongTimeAgreementId();
                doReturn(Crypto.INSTANCE.generateSessionKeyPair(IdentityTestUtil.ID_1.getKeyAgreementKeyPair(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey())).when(session).getLongTimeAgreementPair();

                doReturn(IdentityTestUtil.ID_1.getKeyAgreementKeyPair()).when(agreement).getKeyPair();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1);
            }
        }

        @Test
        void shouldDoNothingIfCurrentAgreementIsNotRenewableOnOutbound(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                       @Mock final
                                                                       ConcurrentReference<Agreement> concurrentAgreement,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                       @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                       @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(concurrentAgreement).when(session).getCurrentActiveAgreement();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).getValue();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).computeOnCondition(any(), any());
                doReturn(Optional.of(mock(SessionPair.class))).when(agreement).getSessionPair();
                doReturn(Optional.of(mock(AgreementId.class))).when(agreement).getAgreementId();

                doReturn(msg).when(msg).armAndRelease(any(SessionPair.class));

                doReturn(true).when(agreement).isInitialized();
                doReturn(false).when(agreement).isRenewable();

                final CompletableFuture<Void> future = pipeline.processOutbound(receiveAddress, msg);

                future.join();
                observer.assertValue(msg);
            }
        }

        @Test
        void shouldDoNothingIfCurrentAgreementIsNotRenewableOnInbound(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                                      @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                      @Mock final
                                                                      ConcurrentReference<Agreement> concurrentAgreement,
                                                                      @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                      @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                      @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.outboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(null).when(msg).getAgreementId();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(concurrentAgreement).when(session).getCurrentActiveAgreement();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).computeOnCondition(any(), any());

                doReturn(false).when(agreement).isRenewable();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                future.join();
                observer.assertNoValues();
            }
        }
    }

    @Nested
    class Decryption {
        @Test
        void shouldSkipMessagesNotForMe(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getRecipient();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldSkipMessagesFormMe(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getSender();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldSkipNotEncryptedMessages(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                            @Mock final Map<IdentityPublicKey, Session> sessions,
                                            @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();
                doReturn(session).when(sessions).computeIfAbsent(any(), any());

                doReturn(null).when(msg).getAgreementId();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldDecryptMessageWithLongTimeKey(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                 @Mock final Map<IdentityPublicKey, Session> sessions,
                                                 @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(msg.getAgreementId()).when(session).getLongTimeAgreementId();

                doReturn(msg).when(msg).disarmAndRelease(any());
                doReturn(Protocol.PrivateHeader.newBuilder().setType(Protocol.MessageType.APPLICATION).build()).when(msg).getPrivateHeader();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldDecryptMessageWithLongTimeKeyIfMaxAgreementOptionIsZero(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                           @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                           @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, 0, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getInitializedAgreements().get(any(AgreementId.class)).getSessionPair()).thenReturn(Optional.of(mock(SessionPair.class)));

                doReturn(msg).when(msg).disarmAndRelease(any());

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldDecryptMessageWithPFSKey(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                            @Mock final Map<IdentityPublicKey, Session> sessions,
                                            @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getInitializedAgreements().get(any(AgreementId.class)).getSessionPair()).thenReturn(Optional.of(mock(SessionPair.class)));

                doReturn(msg).when(msg).disarmAndRelease(any());
                doReturn(Protocol.PrivateHeader.newBuilder().setType(Protocol.MessageType.APPLICATION).build()).when(msg).getPrivateHeader();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldDecryptMessageWithPFSKeyAndRemoveStaleAgreementAfterwards(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                             @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                             @Mock final Agreement agreement,
                                                                             @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                final HashMap<AgreementId, Agreement> agreements = new HashMap<>();
                agreements.put(msg.getAgreementId(), agreement);

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getInitializedAgreements()).thenReturn(agreements);
                doReturn(Optional.of(mock(SessionPair.class))).when(agreement).getSessionPair();
                doReturn(true).when(agreement).isStale();

                doReturn(msg).when(msg).disarmAndRelease(any());
                doReturn(Protocol.PrivateHeader.newBuilder().setType(Protocol.MessageType.APPLICATION).build()).when(msg).getPrivateHeader();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
                assertTrue(agreements.isEmpty());
            }
        }

        @Test
        void shouldDecryptMessageIfMessageArrivesBeforeAck(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope<?> msg,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                           @Mock final Map<IdentityPublicKey, Session> sessions,
                                                           @Mock final Agreement agreement,
                                                           @Mock final Crypto crypto) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, IdentityTestUtil.ID_1, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> observer = pipeline.inboundMessages(RemoteEnvelope.class).test();

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                final HashMap<AgreementId, Agreement> agreements = new HashMap<>();
                agreements.put(msg.getAgreementId(), agreement);

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getCurrentInactiveAgreement().getValue()).thenReturn(Optional.of(agreement));
                doReturn(Optional.of(msg.getAgreementId())).when(agreement).getAgreementId();

                // first call must fail, second call not (on second call, the message has initialized the agreement)
                when(session.getInitializedAgreements()).thenReturn(new HashMap<>()).thenReturn(agreements);
                doReturn(Optional.of(mock(SessionPair.class))).when(agreement).getSessionPair();

                doReturn(msg).when(msg).disarmAndRelease(any());
                doReturn(Protocol.PrivateHeader.newBuilder().setType(Protocol.MessageType.APPLICATION).build()).when(msg).getPrivateHeader();

                final CompletableFuture<Void> future = pipeline.processInbound(receiveAddress, msg);

                assertFalse(future.isDone());
                observer.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }
    }
}
