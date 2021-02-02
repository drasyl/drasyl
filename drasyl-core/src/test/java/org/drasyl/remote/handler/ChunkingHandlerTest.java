/*
 * Copyright (c) 2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.UserAgent;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.TypeReference;
import org.drasyl.util.UnsignedShort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkingHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final int remoteMessageMtu = 1024;
    private final int remoteMaxContentLength = 10 * 1024;
    private final Duration messageComposedMessageTransferTimeout = ofSeconds(10);

    @Nested
    class OnIngoingMessage {
        @Nested
        class WhenAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage(@Mock final InetSocketAddressWrapper senderAddress,
                                                    @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(recipient);

                final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final AddressedIntermediateEnvelope<Application> addressedMsg = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, msg);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, addressedMsg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(addressedMsg);

                pipeline.close();
            }

            @Test
            void shouldCacheChunkedMessageIfOtherChunksAreStillMissing(@Mock final InetSocketAddressWrapper senderAddress,
                                                                       @Mock final InetSocketAddressWrapper recipientAddress) throws IOException, InterruptedException, CryptoException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);
                when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(messageComposedMessageTransferTimeout);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                // head chunk
                final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(UnsignedShort.of(2).toBytes()))
                        .build();
                final byte[] bytes = new byte[remoteMessageMtu / 2];
                final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes);

                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                final AddressedIntermediateEnvelope<MessageLite> addressedHeadChunk = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, headChunk);
                pipeline.processInbound(sender, addressedHeadChunk).join();
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();

                ReferenceCountUtil.safeRelease(headChunk);
                pipeline.close();
            }

            @Test
            void shouldBuildMessageAfterReceivingLastMissingChunk(@Mock final InetSocketAddressWrapper senderAddress,
                                                                  @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException, IOException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);
                when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(messageComposedMessageTransferTimeout);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<AddressedIntermediateEnvelope<?>> inboundMessages = pipeline.inboundMessages(new TypeReference<AddressedIntermediateEnvelope<?>>() {
                }).test();

                // normal chunk
                final PublicHeader chunkHeader = PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setChunkNo(ByteString.copyFrom(UnsignedShort.of(1).toBytes()))
                        .build();
                final byte[] chunkBytes = new byte[remoteMessageMtu / 2];
                new Random().nextBytes(chunkBytes);
                final ByteBuf chunkPayload = Unpooled.wrappedBuffer(chunkBytes);

                final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                final AddressedIntermediateEnvelope<MessageLite> addressedChunk = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, chunk);
                pipeline.processInbound(sender, addressedChunk).join();

                // head chunk
                final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(UnsignedShort.of(2).toBytes()))
                        .build();
                final byte[] headChunkBytes = new byte[remoteMessageMtu / 2];
                new Random().nextBytes(headChunkBytes);
                final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(headChunkBytes);

                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                final AddressedIntermediateEnvelope<MessageLite> addressedHeadChunk = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, headChunk);
                pipeline.processInbound(sender, addressedHeadChunk).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> {
                            try {
                                return Objects.deepEquals(Bytes.concat(headChunkBytes, chunkBytes), ByteBufUtil.getBytes(m.getContent().copy()));
                            }
                            finally {
                                ReferenceCountUtil.safeRelease(m);
                            }
                        });
                pipeline.close();
            }

            @Test
            void shouldCompleteExceptionallyWhenChunkedMessageExceedMaxSize(@Mock final InetSocketAddressWrapper senderAddress,
                                                                            @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException, IOException, InterruptedException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);
                when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(messageComposedMessageTransferTimeout);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                // head chunk
                final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(UnsignedShort.of(2).toBytes()))
                        .build();
                final byte[] bytes1 = new byte[remoteMaxContentLength];
                final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes1);

                // normal chunk
                final PublicHeader chunkHeader = PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setChunkNo(ByteString.copyFrom(UnsignedShort.of(1).toBytes()))
                        .build();
                final byte[] bytes = new byte[remoteMaxContentLength];
                final ByteBuf chunkPayload = Unpooled.wrappedBuffer(bytes);

                final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                final AddressedIntermediateEnvelope<MessageLite> addressedChunk = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, chunk);
                pipeline.processInbound(sender, addressedChunk).join();

                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                final AddressedIntermediateEnvelope<MessageLite> addressedHeadChunk = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, headChunk);
                assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, addressedHeadChunk).get());
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();
                pipeline.close();
            }
        }

        @Nested
        class WhenNotAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage(@Mock final InetSocketAddressWrapper senderAddress,
                                                    @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final AddressedIntermediateEnvelope<Application> addressedMsg = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, msg);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, addressedMsg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(addressedMsg);

                pipeline.close();
            }

            @Test
            void shouldPassthroughChunkedMessage(@Mock final InetSocketAddressWrapper senderAddress,
                                                 @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException, IOException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                when(identity.getPublicKey()).thenReturn(sender);

                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<AddressedIntermediateEnvelope<?>> inboundMessages = pipeline.inboundMessages(new TypeReference<AddressedIntermediateEnvelope<?>>() {
                }).test();

                final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(UnsignedShort.of(2).toBytes()))
                        .build();
                final byte[] bytes = new byte[remoteMessageMtu / 2];
                final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes);
                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                final AddressedIntermediateEnvelope<MessageLite> addressedHeadChunk = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, headChunk);
                pipeline.processInbound(sender, addressedHeadChunk).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(p -> p.getContent().isChunk());
                ReferenceCountUtil.safeRelease(addressedHeadChunk);

                pipeline.close();
            }
        }
    }

    @Nested
    class OnOutgoingMessage {
        @Nested
        class FromMe {
            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldPassthroughMessageNotExceedingMtuSize(@Mock final Address address,
                                                             @Mock final InetSocketAddressWrapper senderAddress,
                                                             @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException {
                when(config.getRemoteMessageMtu()).thenReturn(remoteMessageMtu);
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final AddressedIntermediateEnvelope<Application> addressedMsg = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, msg);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, addressedMsg).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(addressedMsg);
                ReferenceCountUtil.safeRelease(addressedMsg);

                pipeline.close();
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldDropMessageExceedingMaximumMessageSize(@Mock final Address address,
                                                              @Mock final InetSocketAddressWrapper senderAddress,
                                                              @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException, InterruptedException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMaxContentLength]);
                final AddressedIntermediateEnvelope<Application> addressedMsg = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, msg);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(address, addressedMsg).get());
                outboundMessages.await(1, SECONDS);
                outboundMessages.assertNoValues();

                pipeline.close();
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldChunkMessageExceedingMtuSize(@Mock final Address address,
                                                    @Mock final InetSocketAddressWrapper senderAddress,
                                                    @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException {
                when(config.getRemoteMessageMtu()).thenReturn(remoteMessageMtu);
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), Crypto.randomBytes(remoteMessageMtu * 2));
                final AddressedIntermediateEnvelope<Application> addressedMsg = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, msg);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<AddressedIntermediateEnvelope<?>> outboundMessages = pipeline.outboundMessages(new TypeReference<AddressedIntermediateEnvelope<?>>() {
                }).test();

                pipeline.processOutbound(address, addressedMsg).join();

                outboundMessages.awaitCount(3)
                        .assertValueCount(3)
                        .assertValueAt(0, m -> {
                            try {
                                return m.getContent().getTotalChunks().getValue() == 3 && m.getContent().copy().readableBytes() == remoteMessageMtu;
                            }
                            finally {
                                ReferenceCountUtil.safeRelease(m.getContent());
                            }
                        })
                        .assertValueAt(1, m -> {
                            try {
                                return m.getContent().getChunkNo().getValue() == 1 && m.getContent().copy().readableBytes() == remoteMessageMtu;
                            }
                            finally {
                                ReferenceCountUtil.safeRelease(m.getContent());
                            }
                        })
                        .assertValueAt(2, m -> {
                            try {
                                return m.getContent().getChunkNo().getValue() == 2 && m.getContent().copy().readableBytes() < remoteMessageMtu;
                            }
                            finally {
                                ReferenceCountUtil.safeRelease(m.getContent());
                            }
                        });

                pipeline.close();
            }
        }

        @Nested
        class NotFromMe {
            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldPassthroughMessage(@Mock final Address address,
                                          @Mock final InetSocketAddressWrapper senderAddress,
                                          @Mock final InetSocketAddressWrapper recipientAddress) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(recipient);

                final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final AddressedIntermediateEnvelope<Application> addressedMsg = new AddressedIntermediateEnvelope<>(senderAddress, recipientAddress, msg);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, addressedMsg).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m.equals(addressedMsg));

                pipeline.close();
            }
        }
    }
}