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

import com.google.protobuf.ByteString;
import com.goterl.lazysodium.utils.SessionPair;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.ArmedMessage;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.FullReadMessage;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.KeyExchangeAcknowledgementMessage;
import org.drasyl.remote.protocol.KeyExchangeMessage;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.ConcurrentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> applicationMessage = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body).
                        setAgreementId(agreementId);

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>((Object) applicationMessage, receiveAddress));

                assertEquals(applicationMessage, ((AddressedMessage<ArmedMessage, InetSocketAddressWrapper>) pipeline.readOutbound()).message().disarm(Crypto.INSTANCE, sessionPairReceiver));
                assertThat(((AddressedMessage<ArmedMessage, InetSocketAddressWrapper>) pipeline.readOutbound()).message().disarm(Crypto.INSTANCE, sessionPairReceiver), instanceOf(KeyExchangeMessage.class));
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithSenderThatIsNotMe() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_3.getIdentityPublicKey(),
                                IdentityTestUtil.ID_3.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body).
                        setAgreementId(agreementId);

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(msg, receiveAddress));

                assertEquals(new AddressedMessage<>(msg, receiveAddress), pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithNoRecipient() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final FullReadMessage<?> msg = DiscoveryMessage.of(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork());

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(msg, receiveAddress));

                assertEquals(new AddressedMessage<>(msg, receiveAddress), pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithLoopback() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body);

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(msg, receiveAddress));

                assertEquals(new AddressedMessage<>(msg, receiveAddress), pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
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

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body).
                        setAgreementId(agreementId);

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(msg, receiveAddress));

                assertEquals(msg, ((AddressedMessage<ArmedMessage, InetSocketAddressWrapper>) pipeline.readOutbound()).message().disarm(Crypto.INSTANCE, sessionPairReceiver));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class KeyExchange {
        @Test
        void shouldSendKeyExchangeMessageOnOutbound() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body);

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertThat(((AddressedMessage<ArmedMessage, InetSocketAddressWrapper>) pipeline.readOutbound()).message().disarm(Crypto.INSTANCE, sessionPairReceiver), instanceOf(ApplicationMessage.class));
                assertThat(((AddressedMessage<ArmedMessage, InetSocketAddressWrapper>) pipeline.readOutbound()).message().disarm(Crypto.INSTANCE, sessionPairReceiver), instanceOf(KeyExchangeMessage.class));
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldNotSendKeyExchangeMessageOnOutboundIfMaxAgreementOptionIsZero() {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, 0, sessionExpireTime, sessionRetryInterval);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork(),
                        IdentityTestUtil.ID_2.getIdentityPublicKey(),
                        body.getClass().getName(),
                        body);

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(msg, receiveAddress));

                assertNotNull(pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldNotSendKeyExchangeMessageOnInbound() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_2, peersManager, handler);
            try {
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body)
                        .setAgreementId(agreementId);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg.arm(Crypto.INSTANCE, sessionPairSender), (Address) receiveAddress));

                assertNull(pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldSendKeyExchangeMessageOnInboundOnUnknownAgreementId() throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                // construct wrong agreement id
                final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_3.getKeyAgreementPublicKey());

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final FullReadMessage<?> msg = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                IdentityTestUtil.ID_2.getProofOfWork(),
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body)
                        .setAgreementId(agreementId);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg.arm(Crypto.INSTANCE, sessionPairSender), (Address) receiveAddress));

                assertThat(((AddressedMessage<ArmedMessage, Address>) pipeline.readOutbound()).message().disarmAndRelease(Crypto.INSTANCE, sessionPairReceiver), instanceOf(KeyExchangeMessage.class));
            }
            finally {
                pipeline.drasylClose();
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

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_2, peersManager, handler);
            try {
                final TestObserver<Event> observer = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final ArmedMessage msg = KeyExchangeAcknowledgementMessage.of(networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                agreementId2)
                        .setAgreementId(agreementId)
                        .arm(Crypto.INSTANCE, sessionPairSender);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertTrue(session.getCurrentActiveAgreement().getValue().isPresent());
                assertEquals(agreementId2, session.getCurrentActiveAgreement().getValue().get().getAgreementId().get());
                assertTrue(session.getInitializedAgreements().containsKey(agreementId2));
                observer.assertValueCount(1)
                        .assertValue(v -> v instanceof PerfectForwardSecrecyEncryptionEvent);
            }
            finally {
                pipeline.drasylClose();
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

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_2, peersManager, handler);
            try {
                final TestObserver<Event> observer = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final RemoteMessage msg = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body)
                        .setAgreementId(agreementId2)
                        .arm(Crypto.INSTANCE, sessionPairSender);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertTrue(session.getCurrentActiveAgreement().getValue().isPresent());
                assertEquals(agreementId2, session.getCurrentActiveAgreement().getValue().get().getAgreementId().get());
                assertTrue(session.getInitializedAgreements().containsKey(agreementId2));
                observer.assertValueCount(1)
                        .assertValue(v -> v instanceof PerfectForwardSecrecyEncryptionEvent);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldResendKeyExchangeMessageOnInvalidAgreementId() throws InvalidMessageFormatException {
            // construct not existing agreementId
            final AgreementId agreementId = AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());

            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_2, peersManager, handler);
            try {
                final TestObserver<Event> observerEvents = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final ByteString body = ByteString.copyFrom(randomBytes(10));
                final RemoteMessage msg = ApplicationMessage.of(
                                networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                body.getClass().getName(),
                                body)
                        .setAgreementId(agreementId)
                        .arm(Crypto.INSTANCE, sessionPairSender);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertThat(((AddressedMessage<ArmedMessage, Address>) pipeline.readOutbound()).message().disarm(Crypto.INSTANCE, sessionPairSender), instanceOf(KeyExchangeMessage.class));
                observerEvents.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
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

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_2, peersManager, handler);
            try {
                final TestObserver<Event> observerEvents = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final RemoteMessage msg = KeyExchangeMessage.of(networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                keyPair.getPublicKey())
                        .setAgreementId(agreementId)
                        .arm(Crypto.INSTANCE, sessionPairSender);

                assertFalse(session.getCurrentInactiveAgreement().getValue().isPresent());
                assertEquals(0, session.getInitializedAgreements().size());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));
                pipeline.runPendingTasks();

                assertTrue(session.getCurrentInactiveAgreement().getValue().isPresent());
                assertEquals(0, session.getInitializedAgreements().size());
                observerEvents.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
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

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_2, peersManager, handler);
            try {
                final TestObserver<Event> observerEvents = pipeline.inboundEvents().test();

                when(config.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_2.getIdentityPublicKey());
                when(config.getIdentityProofOfWork()).thenReturn(IdentityTestUtil.ID_2.getProofOfWork());

                final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair = Crypto.INSTANCE.generateEphemeralKeyPair();

                final RemoteMessage msg = KeyExchangeMessage.of(networkId,
                                IdentityTestUtil.ID_1.getIdentityPublicKey(),
                                IdentityTestUtil.ID_1.getProofOfWork(),
                                IdentityTestUtil.ID_2.getIdentityPublicKey(),
                                keyPair.getPublicKey())
                        .setAgreementId(agreementId)
                        .arm(Crypto.INSTANCE, sessionPairSender);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));
                pipeline.runPendingTasks();

                assertTrue(session.getCurrentInactiveAgreement().getValue().isPresent());
                assertEquals(keyPair.getPublicKey(), session.getCurrentInactiveAgreement().getValue().get().getRecipientsKeyAgreementKey().get());
                observerEvents.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class Renew {
        @Test
        void shouldInitiateKeyExchangeIfCurrentAgreementIsRenewableOnOutbound(@Mock(answer = RETURNS_DEEP_STUBS) final FullReadMessage<?> msg,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                              @Mock final
                                                                              ConcurrentReference<Agreement> concurrentAgreement,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                              @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                              @Mock final Crypto crypto,
                                                                              @Mock final ArmedMessage armedMsg) throws InvalidMessageFormatException, CryptoException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
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

                doReturn(msg).when(msg).setAgreementId(any());
                doReturn(armedMsg).when(msg).arm(any(Crypto.class), any(SessionPair.class));

                doReturn(new AtomicLong(System.currentTimeMillis() - (sessionExpireTime.toMillis() / Agreement.RENEW_DIVISOR) * 2)).when(session).getLastRenewAttemptAt();
                doReturn(true).when(agreement).isInitialized();
                doReturn(true).when(agreement).isRenewable();
                doReturn(AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey())).when(session).getLongTimeAgreementId();
                doReturn(Crypto.INSTANCE.generateSessionKeyPair(IdentityTestUtil.ID_1.getKeyAgreementKeyPair(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey())).when(session).getLongTimeAgreementPair();

                doReturn(IdentityTestUtil.ID_1.getKeyAgreementKeyPair()).when(agreement).getKeyPair();

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(new AddressedMessage<>(armedMsg, receiveAddress), pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldInitiateKeyExchangeIfCurrentAgreementIsRenewableOnInbound(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                             @Mock final
                                                                             ConcurrentReference<Agreement> concurrentAgreement,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                             @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                             @Mock final Crypto crypto) throws CryptoException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(new byte[]{}).when(crypto).encrypt(any(), any(), any(), any());

                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

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

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertNotNull(pipeline.readOutbound());
                assertNotNull(pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDoNothingIfCurrentAgreementIsNotRenewableOnOutbound(@Mock(answer = RETURNS_DEEP_STUBS) final FullReadMessage<?> msg,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                       @Mock final
                                                                       ConcurrentReference<Agreement> concurrentAgreement,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement,
                                                                       @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                       @Mock final Crypto crypto,
                                                                       @Mock final ArmedMessage armedMsg) throws InvalidMessageFormatException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(concurrentAgreement).when(session).getCurrentActiveAgreement();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).getValue();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).computeOnCondition(any(), any());
                doReturn(Optional.of(mock(SessionPair.class))).when(agreement).getSessionPair();
                doReturn(Optional.of(mock(AgreementId.class))).when(agreement).getAgreementId();

                doReturn(msg).when(msg).setAgreementId(any());
                doReturn(armedMsg).when(msg).arm(any(Crypto.class), any(SessionPair.class));

                doReturn(true).when(agreement).isInitialized();
                doReturn(false).when(agreement).isRenewable();

                pipeline.pipeline().writeAndFlush(new AddressedMessage<>(msg, receiveAddress));

                assertThat(((AddressedMessage<RemoteMessage, Address>) pipeline.readOutbound()).message(), instanceOf(RemoteMessage.class));
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDoNothingIfCurrentAgreementIsNotRenewableOnInbound(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                                                      @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                      @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                      @Mock final Crypto crypto,
                                                                      @Mock final AgreementId agreementId,
                                                                      @Mock final
                                                                      ConcurrentReference<Agreement> concurrentAgreement,
                                                                      @Mock(answer = RETURNS_DEEP_STUBS) final Agreement agreement) {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();
                doReturn(agreementId).when(msg).getAgreementId();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(sessionPairSender).when(session).getLongTimeAgreementPair();
                doReturn(agreementId).when(session).getLongTimeAgreementId();

                doReturn(concurrentAgreement).when(session).getCurrentActiveAgreement();
                doReturn(Optional.of(agreement)).when(concurrentAgreement).computeOnCondition(any(), any());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertNull(pipeline.readOutbound());
                assertThat(((AddressedMessage<Object, Address>) pipeline.readInbound()).message(), instanceOf(FullReadMessage.class));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class Decryption {
        @Test
        void shouldSkipMessagesNotForMe(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg) {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getRecipient();

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(msg, ((AddressedMessage<Object, Address>) pipeline.readInbound()).message());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldSkipMessagesFromMe(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg) {
            final ArmHandler handler = new ArmHandler(maxSessionsCount, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getSender();

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(msg, ((AddressedMessage<Object, Address>) pipeline.readInbound()).message());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDecryptMessageWithLongTimeKey(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                 @Mock final Map<IdentityPublicKey, Session> sessions,
                                                 @Mock final Crypto crypto,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage disarmedMessage) throws IOException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                doReturn(msg.getAgreementId()).when(session).getLongTimeAgreementId();

                doReturn(disarmedMessage).when(msg).disarmAndRelease(any(Crypto.class), any());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(disarmedMessage, ((AddressedMessage<Object, Address>) pipeline.readInbound()).message());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDecryptMessageWithLongTimeKeyIfMaxAgreementOptionIsZero(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                           @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                           @Mock final Crypto crypto,
                                                                           @Mock final FullReadMessage<?> disarmedMsg) throws IOException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, 0, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getInitializedAgreements().get(any(AgreementId.class)).getSessionPair()).thenReturn(Optional.of(mock(SessionPair.class)));

                doReturn(disarmedMsg).when(msg).disarmAndRelease(any(Crypto.class), any());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(disarmedMsg, ((AddressedMessage<Object, Address>) pipeline.readInbound()).message());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDecryptMessageWithPFSKey(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                            @Mock final Map<IdentityPublicKey, Session> sessions,
                                            @Mock final Crypto crypto,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage disarmedMessage) throws IOException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getInitializedAgreements().get(any(AgreementId.class)).getSessionPair()).thenReturn(Optional.of(mock(SessionPair.class)));

                doReturn(disarmedMessage).when(msg).disarmAndRelease(any(Crypto.class), any());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(disarmedMessage, ((AddressedMessage<Object, Address>) pipeline.readInbound()).message());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDecryptMessageWithPFSKeyAndRemoveStaleAgreementAfterwards(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                                             @Mock final Map<IdentityPublicKey, Session> sessions,
                                                                             @Mock final Agreement agreement,
                                                                             @Mock final Crypto crypto,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage disarmedMessage) throws IOException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getSender();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();

                final HashMap<AgreementId, Agreement> agreements = new HashMap<>();
                agreements.put(msg.getAgreementId(), agreement);

                doReturn(session).when(sessions).computeIfAbsent(any(), any());
                when(session.getInitializedAgreements()).thenReturn(agreements);
                doReturn(Optional.of(mock(SessionPair.class))).when(agreement).getSessionPair();
                doReturn(true).when(agreement).isStale();

                doReturn(disarmedMessage).when(msg).disarmAndRelease(any(Crypto.class), any());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, receiveAddress));

                assertEquals(new AddressedMessage<>(disarmedMessage, receiveAddress), pipeline.readInbound());
                assertTrue(agreements.isEmpty());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDecryptMessageIfMessageArrivesBeforeAck(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedMessage msg,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final Session session,
                                                           @Mock final Map<IdentityPublicKey, Session> sessions,
                                                           @Mock final Agreement agreement,
                                                           @Mock final Crypto crypto,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage disarmedMessage) throws IOException {
            final ArmHandler handler = new ArmHandler(sessions, crypto, maxAgreements, sessionExpireTime, sessionRetryInterval);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, handler);
            try {
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

                doReturn(disarmedMessage).when(msg).disarmAndRelease(any(Crypto.class), any());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>((Object) msg, (Address) receiveAddress));

                assertEquals(disarmedMessage, ((AddressedMessage<Object, Address>) pipeline.readInbound()).message());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
