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
package org.drasyl.node.handler.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.node.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.util.ConcurrentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PFSArmHandlerTest {
    private Agreement agreementSenderLongTime;
    private Duration sessionExpireTime;
    private Duration sessionRetryInterval;
    private int maxAgreements;

    @BeforeEach
    void setUp() throws CryptoException {
        final SessionPair sessionPairSender = Crypto.INSTANCE.generateSessionKeyPair(
                IdentityTestUtil.ID_1.getKeyAgreementKeyPair(),
                IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
        final AgreementId agreementIdLongTime = AgreementId.of(IdentityTestUtil.ID_1.getKeyAgreementPublicKey(), IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
        agreementSenderLongTime = Agreement.of(agreementIdLongTime, sessionPairSender, -1);

        sessionExpireTime = Duration.ofSeconds(100000);
        sessionRetryInterval = Duration.ofSeconds(1000);
        maxAgreements = 2;
    }

    @Nested
    class Encryption {
        @Test
        void shouldEncryptOutboundMessageWithLongTimeKey() throws CryptoException {
            final PFSArmHandler handler = new PFSArmHandler(Crypto.INSTANCE, sessionExpireTime, sessionRetryInterval, maxAgreements, IdentityTestUtil.ID_1, IdentityTestUtil.ID_2.getIdentityPublicKey());
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ByteBuf msg = Unpooled.buffer();

                channel.writeAndFlush(msg);

                assertThat(channel.readOutbound(), instanceOf(ArmHeader.class));
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldEncryptOutboundMessageWithEphemeralKey(@Mock final Crypto crypto,
                                                          @Mock final Session session,
                                                          @Mock final ConcurrentReference<Agreement> actualAgreement,
                                                          @Mock final Agreement agreement) throws CryptoException {
            final byte[] enc = new byte[]{ 1, 2, 3 };

            when(session.getCurrentActiveAgreement()).thenReturn(actualAgreement);
            when(actualAgreement.getValue()).thenReturn(Optional.of(agreement));
            when(crypto.encrypt(any(), any(), any(), any())).thenReturn(enc);

            final PFSArmHandler handler = new PFSArmHandler(crypto, IdentityTestUtil.ID_1, IdentityTestUtil.ID_2.getIdentityPublicKey(), session, System::currentTimeMillis, sessionRetryInterval, PFSArmHandler.State.PFS);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ByteBuf msg = Unpooled.buffer();

                channel.writeAndFlush(msg);

                final ArmHeader actual = channel.readOutbound();

                assertThat(actual, instanceOf(ArmHeader.class));
                assertArrayEquals(enc, ByteBufUtil.getBytes(actual.content()));

                verify(session, never()).getLongTimeAgreement();
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Decryption {
        @Test
        void shouldDecryptInboundMessageWithLongTimeKey(@Mock final Crypto crypto,
                                                        @Mock final Session session,
                                                        @Mock final Agreement agreement,
                                                        @Mock(answer = Answers.RETURNS_DEEP_STUBS) final PendingAgreement pendingAgreement,
                                                        @Mock final AgreementId agreementId,
                                                        @Mock final Nonce nonce,
                                                        @Mock final SessionPair sessionPair,
                                                        @Mock final ConcurrentReference<Agreement> currentAgreement,
                                                        @Mock final ConcurrentReference<PendingAgreement> currentInactiveAgreement) throws CryptoException {
            final ByteBuf byteBuf = Unpooled.buffer()
                    .writeByte(ArmMessage.MessageType.APPLICATION.getByte());

            when(session.getCurrentActiveAgreement()).thenReturn(currentAgreement);
            when(currentAgreement.computeOnCondition(any(), any())).thenReturn(Optional.empty());
            when(session.getCurrentInactiveAgreement()).thenReturn(currentInactiveAgreement);
            when(currentInactiveAgreement.computeOnCondition(any(), any())).thenReturn(Optional.of(pendingAgreement));
            when(currentInactiveAgreement.computeIfAbsent(any())).thenReturn(pendingAgreement);
            when(pendingAgreement.getKeyPair().getPublicKey()).thenReturn(IdentityTestUtil.ID_1.getKeyAgreementPublicKey());

            when(session.getLongTimeAgreement()).thenReturn(agreement);
            when(agreement.getAgreementId()).thenReturn(agreementId);
            when(agreement.getSessionPair()).thenReturn(sessionPair);
            when(crypto.decrypt(any(), any(), any(), any())).thenReturn(ByteBufUtil.getBytes(byteBuf));
            when(crypto.encrypt(any(), any(), any(), any())).thenReturn(new byte[0]);

            final PFSArmHandler handler = new PFSArmHandler(crypto, IdentityTestUtil.ID_1, IdentityTestUtil.ID_2.getIdentityPublicKey(), session, System::currentTimeMillis, sessionRetryInterval, PFSArmHandler.State.LONG_TIME);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ArmHeader msg = ArmHeader.of(agreementId, nonce, byteBuf);

                channel.writeInbound(msg);

                assertThat(channel.readInbound(), instanceOf(ByteBuf.class));
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldDecryptInboundMessageWithEphemeralKey(@Mock final Crypto crypto,
                                                         @Mock(answer = Answers.RETURNS_DEEP_STUBS) final Session session,
                                                         @Mock final Agreement agreement,
                                                         @Mock(answer = Answers.RETURNS_DEEP_STUBS) final PendingAgreement pendingAgreement,
                                                         @Mock final AgreementId agreementId,
                                                         @Mock final Nonce nonce,
                                                         @Mock final SessionPair sessionPair,
                                                         @Mock final ConcurrentReference<Agreement> currentAgreement,
                                                         @Mock final ConcurrentReference<PendingAgreement> currentInactiveAgreement) throws CryptoException {
            final ByteBuf byteBuf = Unpooled.buffer()
                    .writeByte(ArmMessage.MessageType.APPLICATION.getByte());

            when(session.getCurrentActiveAgreement()).thenReturn(currentAgreement);
            when(currentAgreement.computeOnCondition(any(), any())).thenReturn(Optional.empty());
            when(session.getCurrentInactiveAgreement()).thenReturn(currentInactiveAgreement);
            when(currentInactiveAgreement.computeOnCondition(any(), any())).thenReturn(Optional.of(pendingAgreement));
            when(currentInactiveAgreement.computeIfAbsent(any())).thenReturn(pendingAgreement);
            when(pendingAgreement.getKeyPair().getPublicKey()).thenReturn(IdentityTestUtil.ID_1.getKeyAgreementPublicKey());

            when(session.getLongTimeAgreement()).thenReturn(agreement);

            when(session.getInitializedAgreements().get(any(AgreementId.class))).thenReturn(agreement);
            when(agreement.getSessionPair()).thenReturn(sessionPair);
            when(crypto.decrypt(any(), any(), any(), any())).thenReturn(ByteBufUtil.getBytes(byteBuf));
            when(crypto.encrypt(any(), any(), any(), any())).thenReturn(new byte[0]);

            final PFSArmHandler handler = new PFSArmHandler(crypto, IdentityTestUtil.ID_1, IdentityTestUtil.ID_2.getIdentityPublicKey(), session, System::currentTimeMillis, sessionRetryInterval, PFSArmHandler.State.LONG_TIME);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ArmHeader msg = ArmHeader.of(agreementId, nonce, byteBuf);

                channel.writeInbound(msg);

                assertThat(channel.readInbound(), instanceOf(ByteBuf.class));
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class KeyExchange {
        @Test
        void shouldDoKeyExchangeOnKeyExchangeMessage() throws CryptoException {
            final KeyExchangeMessage msg = KeyExchangeMessage.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey());

            final PFSArmHandler handler = new PFSArmHandler(Crypto.INSTANCE, sessionExpireTime, sessionRetryInterval, maxAgreements, IdentityTestUtil.ID_2, IdentityTestUtil.ID_1.getIdentityPublicKey());
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelHandlerContext ctx = channel.pipeline().context(handler);
                final ByteBuf byteBuf = ctx.alloc().buffer();
                msg.writeTo(byteBuf);
                channel.writeInbound(handler.arm(ctx, agreementSenderLongTime, byteBuf));

                final ArmHeader actual1 = channel.readOutbound();
                final ArmHeader actual2 = channel.readOutbound();

                assertThat(handler.unarm(ctx, agreementSenderLongTime, actual1.getNonce(), actual1.content()), instanceOf(KeyExchangeMessage.class));
                assertThat(handler.unarm(ctx, agreementSenderLongTime, actual2.getNonce(), actual2.content()), instanceOf(AcknowledgementMessage.class));
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldFireEventOnAck() throws CryptoException {
            final KeyExchangeMessage msg = KeyExchangeMessage.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey());

            final PFSArmHandler handler = new PFSArmHandler(Crypto.INSTANCE, sessionExpireTime, sessionRetryInterval, maxAgreements, IdentityTestUtil.ID_2, IdentityTestUtil.ID_1.getIdentityPublicKey());
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                final ChannelHandlerContext ctx = channel.pipeline().context(handler);
                final ByteBuf byteBuf = ctx.alloc().buffer();
                msg.writeTo(byteBuf);
                channel.writeInbound(handler.arm(ctx, agreementSenderLongTime, byteBuf));

                final ArmHeader actual1 = channel.readOutbound();
                final ArmHeader actual2 = channel.readOutbound();

                assertThat(handler.unarm(ctx, agreementSenderLongTime, actual2.getNonce(), actual2.content()), instanceOf(AcknowledgementMessage.class));

                final KeyExchangeMessage keyExchangeMessage = (KeyExchangeMessage) handler.unarm(ctx, agreementSenderLongTime, actual1.getNonce(), actual1.content());

                // Send ACK
                final AcknowledgementMessage ack = AcknowledgementMessage.of(AgreementId.of(IdentityTestUtil.ID_3.getKeyAgreementPublicKey(), keyExchangeMessage.getSessionKey()));
                final ByteBuf byteBuf2 = ctx.alloc().buffer();
                ack.writeTo(byteBuf2);
                channel.writeInbound(handler.arm(ctx, agreementSenderLongTime, byteBuf2));

                assertThat(channel.readEvent(), instanceOf(PerfectForwardSecrecyEncryptionEvent.class));
            }
            finally {
                channel.close();
            }
        }
    }
}
