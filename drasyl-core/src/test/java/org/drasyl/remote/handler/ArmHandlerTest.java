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

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.TypeReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArmHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private ProofOfWork proofOfWork;

    @Nested
    class OutboundMessages {
        @Test
        void shouldArmOutgoingMessageFromMe(@Mock final InetSocketAddressWrapper recipient) throws ExecutionException, InterruptedException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{});

            final ArmHandler handler = ArmHandler.INSTANCE;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<IntermediateEnvelope<MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<IntermediateEnvelope<MessageLite>>() {
                }).test();

                pipeline.processOutbound(recipient, message).get();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> {
                            try {
                                return m.isArmed();
                            }
                            finally {
                                m.releaseAll();
                            }
                        });
            }
        }

        @Test
        void shouldPassthroughOutgoingMessageNotFromMe(@Mock final CompressedPublicKey recipient) throws ExecutionException, InterruptedException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = IntermediateEnvelope.application(1, CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{});

            final ArmHandler handler = ArmHandler.INSTANCE;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<IntermediateEnvelope<? extends MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<IntermediateEnvelope<? extends MessageLite>>() {
                }).test();

                pipeline.processOutbound(recipient, messageEnvelope).get();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(IntermediateEnvelope::isDisarmed);
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyAndNotPassOutgoingMessageIfArmingFailed(@Mock final InetSocketAddressWrapper recipient) throws InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> message = spy(IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}));
            try (final IntermediateEnvelope<Application> armedMessage = message.armAndRelease(identity.getPrivateKey())) {
                when(armedMessage).thenThrow(IllegalStateException.class);

                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<IntermediateEnvelope<? extends MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<IntermediateEnvelope<? extends MessageLite>>() {
                    }).test();

                    assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());
                    outboundMessages.await(1, SECONDS);
                    outboundMessages.assertNoValues();
                }
            }
        }
    }

    @Nested
    class InboundMessages {
        @Test
        void shouldDisarmIngoingMessageAddressedToMe(@Mock final InetSocketAddressWrapper sender) throws ExecutionException, InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}).armAndRelease(identity.getPrivateKey());

            final ArmHandler handler = ArmHandler.INSTANCE;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<IntermediateEnvelope<? extends MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<IntermediateEnvelope<? extends MessageLite>>() {
                }).test();

                pipeline.processInbound(sender, message).get();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m.getPrivateHeader() != null);
            }
        }

        @Test
        void shouldPassthroughIngoingMessageNotAddressedToMe(@Mock final CompressedPublicKey sender) throws ExecutionException, InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            try (final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), byte[].class.getName(), new byte[]{}).armAndRelease(identity.getPrivateKey())) {
                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<IntermediateEnvelope<? extends MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<IntermediateEnvelope<? extends MessageLite>>() {
                    }).test();

                    pipeline.processInbound(sender, message).get();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(message);
                }
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyAndNotPassIngoingMessageIfDisarmingFailed(@Mock final CompressedPublicKey sender) throws InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[10]).armAndRelease(identity.getPrivateKey());
            // override last n bytes of message to corrupt it
            final ByteBuf byteBuf = message.getInternalByteBuf();
            byteBuf.writerIndex(byteBuf.writerIndex() - 10);
            byteBuf.writeZero(10);

            final ArmHandler handler = ArmHandler.INSTANCE;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();
            }
        }
    }
}
