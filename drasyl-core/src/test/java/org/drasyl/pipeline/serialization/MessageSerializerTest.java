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
package org.drasyl.pipeline.serialization;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.serialization.Serializer;
import org.drasyl.serialization.StringSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.pipeline.EmbeddedPipeline.NULL_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageSerializerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Nested
    class OnInboundMessage {
        @Test
        void shouldDeserializeMessageIfSerializerForConcreteClassExist(@Mock final CompressedPublicKey address) {
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", new StringSerializer()));
            when(config.getSerializationsBindingsInbound()).thenReturn(Map.of(String.class, "string"));
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(16425882), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), String.class.getName(), "Hallo Welt".getBytes())) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    pipeline.processInbound(address, message).join();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue("Hallo Welt");
                }
            }
        }

        @Test
        void shouldDeserializeMessageIfSerializerForSuperClassExist(@Mock final CompressedPublicKey address,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Serializer serializer) throws IOException {
            when(config.getSerializationSerializers()).thenReturn(Map.of("object", serializer));
            when(config.getSerializationsBindingsInbound()).thenReturn(Map.of(Object.class, "object"));
            when(serializer.fromByteArray(any(), anyString())).thenReturn("Hallo Welt");
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(16425882), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), String.class.getName(), "Hallo Welt".getBytes())) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    pipeline.processInbound(address, message).join();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue("Hallo Welt");
                }
            }
        }

        @Test
        void shouldBeAbleToDeserializeNullMessage(@Mock final CompressedPublicKey address) {
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(16425882), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), null, null)) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    pipeline.processInbound(address, message).join();

                    inboundMessages.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(NULL_MESSAGE);
                }
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final CompressedPublicKey sender) throws InterruptedException {
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(16425882), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), String.class.getName(), "Hallo Welt".getBytes())) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());
                    inboundMessages.await(1, SECONDS);
                    inboundMessages.assertNoValues();
                }
            }
        }

        @Test
        void shouldCompleteExceptionallyIfDeserializationFail(@Mock final CompressedPublicKey sender,
                                                              @Mock final Serializer serializer) throws IOException, InterruptedException {
            when(serializer.fromByteArray(any(), anyString())).thenThrow(IOException.class);
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", serializer));
            when(config.getSerializationsBindingsInbound()).thenReturn(Map.of(String.class, "string"));
            try (final RemoteEnvelope<Application> message = RemoteEnvelope.application(1, CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), ProofOfWork.of(16425882), CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), String.class.getName(), "Hallo Welt".getBytes())) {
                try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                    final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                    assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());
                    inboundMessages.await(1, SECONDS);
                    inboundMessages.assertNoValues();
                }
            }
        }
    }

    @Nested
    class OnOutboundMessage {
        @Test
        void shouldSerializeMessageIfForConcreteClassSerializerExist() {
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", new StringSerializer()));
            when(config.getSerializationsBindingsOutbound()).thenReturn(Map.of(String.class, "string"));

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(identity.getPublicKey(), "Hello World").join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof RemoteEnvelope);
            }
        }

        @Test
        void shouldSerializeMessageIfForSuperClassSerializerExist(@Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Serializer serializer) throws IOException {
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            when(config.getSerializationSerializers()).thenReturn(Map.of("object", serializer));
            when(config.getSerializationsBindingsOutbound()).thenReturn(Map.of(Object.class, "object"));
            when(serializer.toByteArray(any())).thenReturn(new byte[0]);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(identity.getPublicKey(), message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof RemoteEnvelope);
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final CompressedPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Object message) throws InterruptedException {
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());
                outboundMessages.await(1, SECONDS);
                outboundMessages.assertNoValues();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializationFail(@Mock final CompressedPublicKey recipient,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                            @Mock final Serializer serializer) throws InterruptedException {
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", serializer));
            when(config.getSerializationsBindingsOutbound()).thenReturn(Map.of(String.class, "string"));

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());
                outboundMessages.await(1, SECONDS);
                outboundMessages.assertNoValues();
            }
        }

        @Test
        void shouldBeAbleToSerializeNullMessage(@Mock final CompressedPublicKey recipient) {
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(identity.getPublicKey(), null);

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof RemoteEnvelope);
            }
        }
    }
}
