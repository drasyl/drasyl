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
package org.drasyl.remote.handler;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.BodyChunkMessage;
import org.drasyl.remote.protocol.ChunkMessage;
import org.drasyl.remote.protocol.HeadChunkMessage;
import org.drasyl.remote.protocol.HopCount;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.PartialReadMessage;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.UnarmedMessage;
import org.drasyl.util.UnsignedShort;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.remote.protocol.Nonce.randomNonce;
import static org.drasyl.util.RandomUtil.randomBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class ChunkingHandlerTest {
    private final int remoteMessageMtu = 1024;
    private final int remoteMaxContentLength = 10 * 1024;
    private final Duration messageComposedMessageTransferTimeout = ofSeconds(10);

    @Nested
    class OnInboundMessage {
        @Nested
        class WhenAddressedToMe {
            @Test
            void shouldCacheChunkedMessageIfOtherChunksAreStillMissing(@Mock final SocketAddress senderAddress) {
                final ChannelInboundHandler handler = new ChunkingHandler(ID_2.getIdentityPublicKey(), remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    final ByteBuf bytes = Unpooled.wrappedBuffer(new byte[remoteMessageMtu / 2]);
                    final HeadChunkMessage headChunk = HeadChunkMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), UnsignedShort.of(2), bytes);
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(headChunk, senderAddress));

                    assertNull(pipeline.readInbound());
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            void shouldBuildMessageAfterReceivingLastMissingChunk(@Mock final SocketAddress senderAddress) throws InvalidMessageFormatException {
                final ChannelInboundHandler handler = new ChunkingHandler(ID_2.getIdentityPublicKey(), remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    final ByteBuf bytes = Unpooled.buffer();
                    final ApplicationMessage message = ApplicationMessage.of(0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFrom(randomBytes(remoteMessageMtu - 200)));
                    message.writeTo(bytes);

                    final BodyChunkMessage bodyChunk = BodyChunkMessage.of(randomNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), UnsignedShort.of(1), bytes.slice(remoteMessageMtu / 2, remoteMessageMtu / 2).copy());
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(bodyChunk, senderAddress));

                    final HeadChunkMessage headChunk = HeadChunkMessage.of(bodyChunk.getNonce(), 0, ID_1.getIdentityPublicKey(), ID_1.getProofOfWork(), ID_2.getIdentityPublicKey(), HopCount.of(), UnsignedShort.of(2), bytes.slice(0, remoteMessageMtu / 2).copy());
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(headChunk, senderAddress));

                    final AddressedMessage<UnarmedMessage, SocketAddress> actual = pipeline.readInbound();
                    assertEquals(message, actual.message().read());

                    actual.release();
                    bytes.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            void shouldCompleteExceptionallyWhenChunkedMessageExceedMaxSize(@Mock final SocketAddress senderAddress) {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();
                final Nonce nonce = randomNonce();

                final ChannelInboundHandler handler = new ChunkingHandler(recipient, remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    // head chunk
                    final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                            .setNonce(nonce.toByteString())
                            .setSender(sender.getBytes())
                            .setRecipient(recipient.getBytes())
                            .setHopCount(1)
                            .setTotalChunks(UnsignedShort.of(2).getValue())
                            .build();
                    final byte[] bytes1 = new byte[remoteMaxContentLength];
                    final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes1);

                    // normal chunk
                    final PublicHeader chunkHeader = PublicHeader.newBuilder()
                            .setNonce(nonce.toByteString())
                            .setSender(sender.getBytes())
                            .setRecipient(recipient.getBytes())
                            .setHopCount(1)
                            .setChunkNo(UnsignedShort.of(1).getValue())
                            .build();
                    final byte[] bytes = new byte[remoteMaxContentLength];
                    final ByteBuf chunkPayload = Unpooled.wrappedBuffer(bytes);

                    final PartialReadMessage chunk = PartialReadMessage.of(chunkHeader, chunkPayload);
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(chunk, senderAddress));

                    final PartialReadMessage headChunk = PartialReadMessage.of(headChunkHeader, headChunkPayload);

                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(headChunk, senderAddress));

                    assertNull(pipeline.readInbound());
                }
                finally {
                    pipeline.close();
                }
            }
        }

        @Nested
        class WhenNotAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();

                final ChannelInboundHandler handler = new ChunkingHandler(ID_1.getIdentityPublicKey(), remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final ApplicationMessage msg = ApplicationMessage.of(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), ByteString.copyFrom(new byte[remoteMessageMtu / 2]));
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, sender));

                    final ReferenceCounted actual = pipeline.readInbound();
                    assertEquals(new AddressedMessage<>(msg, sender), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            void shouldPassthroughChunkedMessage() {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();
                final Nonce nonce = randomNonce();

                final ChannelInboundHandler handler = new ChunkingHandler(ID_1.getIdentityPublicKey(), remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                            .setNonce(nonce.toByteString())
                            .setSender(sender.getBytes())
                            .setRecipient(recipient.getBytes())
                            .setHopCount(1)
                            .setTotalChunks(UnsignedShort.of(2).getValue())
                            .build();
                    final byte[] bytes = new byte[remoteMessageMtu / 2];
                    final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes);
                    final PartialReadMessage headChunk = PartialReadMessage.of(headChunkHeader, headChunkPayload);
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(headChunk, sender));

                    final ReferenceCounted actual = pipeline.readInbound();
                    assertEquals(new AddressedMessage<>(headChunk, sender), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }
        }
    }

    @Nested
    class OnOutgoingMessage {
        @Nested
        class FromMe {
            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldPassthroughMessageNotExceedingMtuSize(@Mock final SocketAddress recipientAddress) throws CryptoException, InvalidMessageFormatException {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();

                final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
                final ChannelInboundHandler handler = new ChunkingHandler(sender, remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final PartialReadMessage msg = ApplicationMessage.of(randomNonce(), 0, sender, ProofOfWork.of(6518542), recipient, HopCount.of(), agreementId, byte[].class.getName(), ByteString.copyFrom(new byte[remoteMessageMtu / 2]))
                        .arm(Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(msg, recipientAddress));

                    final ReferenceCounted actual = pipeline.readOutbound();
                    assertEquals(new AddressedMessage<>(msg, recipientAddress), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldDropMessageExceedingMaximumMessageSize(@Mock final SocketAddress address) {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();

                final ChannelInboundHandler handler = new ChunkingHandler(sender, remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final ApplicationMessage msg = ApplicationMessage.of(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), ByteString.copyFrom(new byte[remoteMaxContentLength]));
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    final ChannelPromise promise = pipeline.newPromise();
                    pipeline.writeAndFlush(new AddressedMessage<>(msg, address), promise);
                    assertFalse(promise.isSuccess());

                    assertNull(pipeline.readOutbound());
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldChunkMessageExceedingMtuSize(@Mock final SocketAddress address) throws CryptoException, InvalidMessageFormatException {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();

                final AgreementId agreementId = AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey());
                final PartialReadMessage msg = ApplicationMessage.of(randomNonce(), 0, sender, ProofOfWork.of(6518542), recipient, HopCount.of(), agreementId, byte[].class.getName(), ByteString.copyFrom(randomBytes(remoteMessageMtu * 2)))
                        .arm(Crypto.INSTANCE, Crypto.INSTANCE.generateSessionKeyPair(ID_1.getKeyAgreementKeyPair(), ID_2.getKeyAgreementPublicKey()));
                final ChannelInboundHandler handler = new ChunkingHandler(sender, remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(msg, address));

                    final AddressedMessage<ChunkMessage, SocketAddress> actual1 = pipeline.readOutbound();
                    assertThat(actual1.message(), new TypeSafeMatcher<>() {
                        @Override
                        public void describeTo(final Description description) {

                        }

                        @Override
                        protected boolean matchesSafely(final ChunkMessage m) {
                            return m instanceof HeadChunkMessage && ((HeadChunkMessage) m).getTotalChunks().getValue() == 3 && m.getBytes().readableBytes() <= remoteMessageMtu;
                        }
                    });
                    final AddressedMessage<ChunkMessage, SocketAddress> actual2 = pipeline.readOutbound();
                    assertThat(actual2.message(), new TypeSafeMatcher<>() {
                        @Override
                        public void describeTo(final Description description) {

                        }

                        @Override
                        protected boolean matchesSafely(final ChunkMessage m) {
                            return m instanceof BodyChunkMessage && ((BodyChunkMessage) m).getChunkNo().getValue() == 1 && m.getBytes().readableBytes() <= remoteMessageMtu;
                        }
                    });
                    final AddressedMessage<ChunkMessage, SocketAddress> actual3 = pipeline.readOutbound();
                    assertThat(actual3.message(), new TypeSafeMatcher<>() {
                        @Override
                        public void describeTo(final Description description) {

                        }

                        @Override
                        protected boolean matchesSafely(final ChunkMessage m) {
                            return m instanceof BodyChunkMessage && ((BodyChunkMessage) m).getChunkNo().getValue() == 2 && m.getBytes().readableBytes() <= remoteMessageMtu;
                        }
                    });

                    actual1.release();
                    actual2.release();
                    actual3.release();
                }
                finally {
                    pipeline.close();
                }
            }
        }

        @Nested
        class NotFromMe {
            @Test
            void shouldPassthroughMessage(@Mock final SocketAddress recipientAddress) {
                final IdentityPublicKey sender = ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = ID_2.getIdentityPublicKey();

                final ChannelInboundHandler handler = new ChunkingHandler(ID_1.getAddress(), remoteMaxContentLength, remoteMessageMtu, messageComposedMessageTransferTimeout);
                final ApplicationMessage msg = ApplicationMessage.of(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), ByteString.copyFrom(new byte[remoteMessageMtu / 2]));
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(msg, recipientAddress));

                    final ReferenceCounted actual = pipeline.readOutbound();
                    assertEquals(new AddressedMessage<>(msg, recipientAddress), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }
        }
    }
}
