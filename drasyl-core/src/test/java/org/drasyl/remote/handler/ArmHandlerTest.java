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
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.TypeReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArmHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
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
            final ArmHandler handler = ArmHandler.INSTANCE;
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{})) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<RemoteEnvelope<MessageLite>>() {
                    }).test();

                    pipeline.processOutbound(recipient, message).get();

                    outboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(RemoteEnvelope::isArmed);
                }
            }
        }

        @Test
        void shouldPassthroughOutgoingMessageNotFromMe(@Mock final CompressedPublicKey recipient) throws ExecutionException, InterruptedException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final ArmHandler handler = ArmHandler.INSTANCE;
            try (final RemoteEnvelope<Application> messageEnvelope = RemoteEnvelope.application(1, CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{})) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<? extends MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<RemoteEnvelope<? extends MessageLite>>() {
                    }).test();

                    pipeline.processOutbound(recipient, messageEnvelope).get();

                    outboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(RemoteEnvelope::isDisarmed);
                }
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyAndNotPassOutgoingMessageIfArmingFailed(@Mock final InetSocketAddressWrapper recipient) throws IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            try (final RemoteEnvelope<Application> message = spy(RemoteEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}))) {
                when(message.arm(identity.getPrivateKey())).thenThrow(IllegalStateException.class);

                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<? extends MessageLite>> outboundMessages = pipeline.outboundMessages(new TypeReference<RemoteEnvelope<? extends MessageLite>>() {
                    }).test();

                    assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());

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
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}).armAndRelease(identity.getPrivateKey())) {
                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<? extends MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<RemoteEnvelope<? extends MessageLite>>() {
                    }).test();

                    pipeline.processInbound(sender, message).get();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(m -> m.getPrivateHeader() != null);
                }
            }
        }

        @Test
        void shouldDisarmIngoingMulticastMessage(@Mock final InetSocketAddressWrapper sender) throws ExecutionException, InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            try (final RemoteEnvelope<Discovery> message = RemoteEnvelope.discovery(1, identity.getPublicKey(), proofOfWork).armAndRelease(identity.getPrivateKey())) {
                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<? extends MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<RemoteEnvelope<? extends MessageLite>>() {
                    }).test();

                    pipeline.processInbound(sender, message).get();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(m -> m.getPrivateHeader() != null);
                }
            }
        }

        @Test
        void shouldPassthroughIngoingMessageNotAddressedToMe(@Mock final CompressedPublicKey sender) throws ExecutionException, InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, identity.getPublicKey(), proofOfWork, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), byte[].class.getName(), new byte[]{}).armAndRelease(identity.getPrivateKey())) {
                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<RemoteEnvelope<? extends MessageLite>> inboundMessages = pipeline.inboundMessages(new TypeReference<RemoteEnvelope<? extends MessageLite>>() {
                    }).test();

                    pipeline.processInbound(sender, message).get();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(message);
                }
            }
        }

        @Test
        void shouldCompleteFutureExceptionallyAndNotPassIngoingMessageIfDisarmingFailed(@Mock final CompressedPublicKey sender) throws IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[10]).armAndRelease(identity.getPrivateKey())) {
                // override last n bytes of message to corrupt it
                final ByteBuf byteBuf = message.getInternalByteBuf();
                byteBuf.writerIndex(byteBuf.writerIndex() - 10);
                byteBuf.writeZero(10);

                final ArmHandler handler = ArmHandler.INSTANCE;
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());

                    inboundMessages.assertNoValues();
                }
            }
        }
    }
}
