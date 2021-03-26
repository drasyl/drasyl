/*
 * Copyright (c) 2020-2021.
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
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.RandomUtil;
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
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkingHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
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
            void shouldPassthroughNonChunkedMessage() {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");

                final Handler handler = new ChunkingHandler();
                try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2])) {
                    try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                        final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithSender().test();

                        pipeline.processInbound(sender, msg).join();

                        inboundMessages.awaitCount(1)
                                .assertValueCount(1)
                                .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
                    }
                }
            }

            @Test
            void shouldCacheChunkedMessageIfOtherChunksAreStillMissing(@Mock final InetSocketAddressWrapper senderAddress) throws IOException, InterruptedException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);
                when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(messageComposedMessageTransferTimeout);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler();
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    // head chunk
                    final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                            .setId(messageId.longValue())
                            .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                            .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                            .setHopCount(1)
                            .setTotalChunks(UnsignedShort.of(2).getValue())
                            .build();
                    final byte[] bytes = new byte[remoteMessageMtu / 2];
                    final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes);

                    try (final RemoteEnvelope<MessageLite> headChunk = RemoteEnvelope.of(headChunkHeader, headChunkPayload)) {
                        pipeline.processInbound(senderAddress, headChunk).join();
                        inboundMessages.await(1, SECONDS);
                        inboundMessages.assertNoValues();
                    }
                }
            }

            @Test
            void shouldBuildMessageAfterReceivingLastMissingChunk(@Mock final InetSocketAddressWrapper senderAddress) throws IOException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);
                when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(messageComposedMessageTransferTimeout);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler();
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<? extends MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<RemoteEnvelope<? extends MessageLite>>() {
                    }).test();

                    // normal chunk
                    final PublicHeader chunkHeader = PublicHeader.newBuilder()
                            .setId(messageId.longValue())
                            .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                            .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                            .setHopCount(1)
                            .setChunkNo(UnsignedShort.of(1).getValue())
                            .build();
                    final byte[] chunkBytes = RandomUtil.randomBytes(remoteMessageMtu / 2);
                    final ByteBuf chunkPayload = Unpooled.wrappedBuffer(chunkBytes);

                    final RemoteEnvelope<MessageLite> chunk = RemoteEnvelope.of(chunkHeader, chunkPayload);
                    pipeline.processInbound(senderAddress, chunk).join();

                    // head chunk
                    final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                            .setId(messageId.longValue())
                            .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                            .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                            .setHopCount(1)
                            .setTotalChunks(UnsignedShort.of(2).getValue())
                            .build();
                    final byte[] headChunkBytes = RandomUtil.randomBytes(remoteMessageMtu / 2);
                    final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(headChunkBytes);

                    final RemoteEnvelope<MessageLite> headChunk = RemoteEnvelope.of(headChunkHeader, headChunkPayload);
                    pipeline.processInbound(senderAddress, headChunk).join();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(m -> {
                                try {
                                    return Objects.deepEquals(Bytes.concat(headChunkBytes, chunkBytes), ByteBufUtil.getBytes(m.copy()));
                                }
                                finally {
                                    m.releaseAll();
                                }
                            });
                }
            }

            @Test
            void shouldCompleteExceptionallyWhenChunkedMessageExceedMaxSize(@Mock final InetSocketAddressWrapper senderAddress) throws IOException, InterruptedException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);
                when(config.getRemoteMessageComposedMessageTransferTimeout()).thenReturn(messageComposedMessageTransferTimeout);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler();
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    // head chunk
                    final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                            .setId(messageId.longValue())
                            .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                            .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                            .setHopCount(1)
                            .setTotalChunks(UnsignedShort.of(2).getValue())
                            .build();
                    final byte[] bytes1 = new byte[remoteMaxContentLength];
                    final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes1);

                    // normal chunk
                    final PublicHeader chunkHeader = PublicHeader.newBuilder()
                            .setId(messageId.longValue())
                            .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                            .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                            .setHopCount(1)
                            .setChunkNo(UnsignedShort.of(1).getValue())
                            .build();
                    final byte[] bytes = new byte[remoteMaxContentLength];
                    final ByteBuf chunkPayload = Unpooled.wrappedBuffer(bytes);

                    final RemoteEnvelope<MessageLite> chunk = RemoteEnvelope.of(chunkHeader, chunkPayload);
                    pipeline.processInbound(senderAddress, chunk).join();

                    final RemoteEnvelope<MessageLite> headChunk = RemoteEnvelope.of(headChunkHeader, headChunkPayload);
                    assertThrows(ExecutionException.class, () -> pipeline.processInbound(senderAddress, headChunk).get());
                    inboundMessages.await(1, SECONDS);
                    inboundMessages.assertNoValues();
                }
            }
        }

        @Nested
        class WhenNotAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");

                final Handler handler = new ChunkingHandler();
                try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2])) {
                    try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                        final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithSender().test();

                        pipeline.processInbound(sender, msg).join();

                        inboundMessages.awaitCount(1)
                                .assertValueCount(1)
                                .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
                    }
                }
            }

            @Test
            void shouldPassthroughChunkedMessage() throws IOException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();

                final Handler handler = new ChunkingHandler();
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<RemoteEnvelope<MessageLite>>() {
                    }).test();

                    final PublicHeader headChunkHeader = PublicHeader.newBuilder()
                            .setId(messageId.longValue())
                            .setSender(ByteString.copyFrom(sender.byteArrayValue()))
                            .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                            .setHopCount(1)
                            .setTotalChunks(UnsignedShort.of(2).getValue())
                            .build();
                    final byte[] bytes = new byte[remoteMessageMtu / 2];
                    final ByteBuf headChunkPayload = Unpooled.wrappedBuffer(bytes);
                    try (final RemoteEnvelope<MessageLite> headChunk = RemoteEnvelope.of(headChunkHeader, headChunkPayload)) {
                        pipeline.processInbound(sender, headChunk).join();

                        inboundMessages.awaitCount(1)
                                .assertValueCount(1)
                                .assertValue(RemoteEnvelope::isChunk);
                    }
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
            void shouldPassthroughMessageNotExceedingMtuSize(@Mock final InetSocketAddressWrapper recipientAddress) {
                when(config.getRemoteMessageMtu()).thenReturn(remoteMessageMtu);
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final Handler handler = new ChunkingHandler();
                try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2])) {
                    try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                        final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

                        pipeline.processOutbound(recipientAddress, msg).join();

                        outboundMessages.awaitCount(1)
                                .assertValueCount(1)
                                .assertValue(new DefaultAddressedEnvelope<>(null, recipientAddress, msg));
                    }
                }
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldDropMessageExceedingMaximumMessageSize(@Mock final InetSocketAddressWrapper address) throws InterruptedException {
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final Handler handler = new ChunkingHandler();
                try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMaxContentLength])) {
                    try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                        final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                        assertThrows(ExecutionException.class, () -> pipeline.processOutbound(address, msg).get());
                        outboundMessages.await(1, SECONDS);
                        outboundMessages.assertNoValues();
                    }
                }
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldChunkMessageExceedingMtuSize(@Mock final InetSocketAddressWrapper address) {
                when(config.getRemoteMessageMtu()).thenReturn(remoteMessageMtu);
                when(config.getRemoteMessageMaxContentLength()).thenReturn(remoteMaxContentLength);

                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), RandomUtil.randomBytes(remoteMessageMtu * 2))) {
                    final Handler handler = new ChunkingHandler();
                    try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                        final TestObserver<RemoteEnvelope<MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<RemoteEnvelope<MessageLite>>() {
                        }).test();

                        pipeline.processOutbound(address, msg).join();

                        outboundMessages.awaitCount(3)
                                .assertValueCount(3)
                                .assertValueAt(0, m -> {
                                    try {
                                        return m.getTotalChunks().getValue() == 3 && m.copy().readableBytes() <= remoteMessageMtu;
                                    }
                                    finally {
                                        m.releaseAll();
                                    }
                                })
                                .assertValueAt(1, m -> {
                                    try {
                                        return m.getChunkNo().getValue() == 1 && m.copy().readableBytes() <= remoteMessageMtu;
                                    }
                                    finally {
                                        m.releaseAll();
                                    }
                                })
                                .assertValueAt(2, m -> {
                                    try {
                                        return m.getChunkNo().getValue() == 2 && m.copy().readableBytes() <= remoteMessageMtu;
                                    }
                                    finally {
                                        m.releaseAll();
                                    }
                                });
                    }
                }
            }
        }

        @Nested
        class NotFromMe {
            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldPassthroughMessage(@Mock final InetSocketAddressWrapper recipientAddress) {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");

                final Handler handler = new ChunkingHandler();
                try (final RemoteEnvelope<Application> msg = RemoteEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2])) {
                    try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                        final @NonNull TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

                        pipeline.processOutbound(recipientAddress, msg).join();

                        outboundMessages.awaitCount(1)
                                .assertValueCount(1)
                                .assertValue(new DefaultAddressedEnvelope<>(null, recipientAddress, msg));
                    }
                }
            }
        }
    }
}
