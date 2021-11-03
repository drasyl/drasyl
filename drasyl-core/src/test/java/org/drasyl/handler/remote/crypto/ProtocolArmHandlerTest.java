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
package org.drasyl.handler.remote.crypto;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.ArmedProtocolMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.handler.remote.protocol.FullReadMessage;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.InvalidMessageFormatException;
import org.drasyl.handler.remote.protocol.Nonce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.drasyl.util.RandomUtil.randomBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class ProtocolArmHandlerTest {
    private int networkId;
    private InetSocketAddress receiveAddress;
    private SessionPair sessionPairSender;
    private SessionPair sessionPairReceiver;
    private Duration sessionExpireTime;
    private int maxSessionsCount;

    @BeforeEach
    void setUp() throws CryptoException {
        networkId = -1;
        receiveAddress = new InetSocketAddress("127.0.0.1", 22527);
        sessionPairSender = Crypto.INSTANCE.generateSessionKeyPair(
                IdentityTestUtil.ID_1.getKeyAgreementKeyPair(),
                IdentityTestUtil.ID_2.getKeyAgreementPublicKey());
        sessionPairReceiver = Crypto.INSTANCE.generateSessionKeyPair(
                IdentityTestUtil.ID_2.getKeyAgreementKeyPair(),
                IdentityTestUtil.ID_1.getKeyAgreementPublicKey());
        sessionExpireTime = Duration.ofSeconds(100000);
        maxSessionsCount = 5;
    }

    @Nested
    class Encryption {
        @Test
        void shouldEncryptOutgoingMessageWithRecipientAndFromMe() throws InvalidMessageFormatException {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_1, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final FullReadMessage<?> applicationMessage = ApplicationMessage.of(HopCount.of(), true, networkId, Nonce.randomNonce(), IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), Unpooled.wrappedBuffer(randomBytes(10)).retain());

                channel.writeAndFlush(new InetAddressedMessage<>(applicationMessage, receiveAddress));

                final InetAddressedMessage<ArmedProtocolMessage> actual1 = channel.readOutbound();

                assertEquals(applicationMessage, actual1.content().disarm(Crypto.INSTANCE, sessionPairReceiver));

                actual1.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithSenderThatIsNotMe() {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_1, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final FullReadMessage<?> msg = ApplicationMessage.of(networkId, IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_3.getIdentityPublicKey(), IdentityTestUtil.ID_3.getProofOfWork(), Unpooled.wrappedBuffer(randomBytes(10)));

                channel.writeAndFlush(new InetAddressedMessage<>(msg, receiveAddress));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(msg, receiveAddress), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithNoRecipient() {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_1, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final FullReadMessage<?> msg = DiscoveryMessage.of(
                        networkId,
                        IdentityTestUtil.ID_1.getIdentityPublicKey(),
                        IdentityTestUtil.ID_1.getProofOfWork());

                channel.writeAndFlush(new InetAddressedMessage<>(msg, receiveAddress));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(msg, receiveAddress), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldNotEncryptOutgoingMessageWithLoopback() {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_1, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final FullReadMessage<?> msg = ApplicationMessage.of(networkId, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), Unpooled.wrappedBuffer(randomBytes(10)));

                channel.writeAndFlush(new InetAddressedMessage<>(msg, receiveAddress));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(msg, receiveAddress), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Decryption {
        @Test
        void shouldSkipMessagesNotForMe(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedProtocolMessage msg) {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_1, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                doReturn(IdentityTestUtil.ID_2.getIdentityPublicKey()).when(msg).getRecipient();

                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, receiveAddress));

                final InetAddressedMessage<Object> actual = channel.readInbound();
                assertEquals(msg, actual.content());

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldSkipMessagesFromMe(@Mock(answer = RETURNS_DEEP_STUBS) final ArmedProtocolMessage msg) {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_1, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getRecipient();
                doReturn(IdentityTestUtil.ID_1.getIdentityPublicKey()).when(msg).getSender();

                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, receiveAddress));

                final InetAddressedMessage<Object> actual = channel.readInbound();
                assertEquals(msg, actual.content());

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldDecryptMessage() throws InvalidMessageFormatException {
            final ProtocolArmHandler handler = new ProtocolArmHandler(IdentityTestUtil.ID_2, Crypto.INSTANCE, maxSessionsCount, sessionExpireTime);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final FullReadMessage<?> applicationMessage = ApplicationMessage.of(HopCount.of(), true, networkId, Nonce.randomNonce(), IdentityTestUtil.ID_2.getIdentityPublicKey(), IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), Unpooled.wrappedBuffer(randomBytes(10)).retain());
                final ArmedProtocolMessage armedMessage = applicationMessage.arm(Unpooled.buffer(), Crypto.INSTANCE, sessionPairSender);

                channel.writeInbound(new InetAddressedMessage<>(armedMessage, receiveAddress));

                final InetAddressedMessage<FullReadMessage<?>> actual1 = channel.readInbound();

                assertEquals(applicationMessage, actual1.content());

                actual1.release();
            }
            finally {
                channel.close();
            }
        }
    }
}
