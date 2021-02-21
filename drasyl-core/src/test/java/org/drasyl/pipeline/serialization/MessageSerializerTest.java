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
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.message.ApplicationMessage;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
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
        void shouldDeserializeMessageIfSerializerForConcreteClassExist(@Mock final CompressedPublicKey address,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) throws ClassNotFoundException {
            when(message.getContent()).thenReturn("Hallo Welt".getBytes());
            when(message.getTypeClazz()).then(invocation -> String.class);
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", new StringSerializer()));
            when(config.getSerializationsBindingsInbound()).thenReturn(Map.of(String.class, "string"));

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(address, message);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof ApplicationMessage);
            }
        }

        @Test
        void shouldDeserializeMessageIfSerializerForSuperClassExist(@Mock final CompressedPublicKey address,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Serializer serializer) throws ClassNotFoundException, IOException {
            when(message.getContent()).thenReturn("Hallo Welt".getBytes());
            when(message.getTypeClazz()).then(invocation -> String.class);
            when(config.getSerializationSerializers()).thenReturn(Map.of("object", serializer));
            when(config.getSerializationsBindingsInbound()).thenReturn(Map.of(Object.class, "object"));
            when(serializer.fromByteArray(any(), any())).thenReturn("Hallo Welt");

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(address, message);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof ApplicationMessage);
            }
        }

        @Test
        void shouldBeAbleToDeserializeNullMessage(@Mock final CompressedPublicKey address) {
            final SerializedApplicationMessage message = new SerializedApplicationMessage(address, address, null, new byte[0]);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(address, message);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof ApplicationMessage && ((ApplicationMessage) m).getContent() == null);
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final CompressedPublicKey sender,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) throws ClassNotFoundException, InterruptedException {
            when(message.getTypeClazz()).then(invocation -> String.class);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfDeserializationFail(@Mock final CompressedPublicKey sender,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message,
                                                              @Mock final Serializer serializer) throws IOException, ClassNotFoundException, InterruptedException {
            when(message.getContent()).thenReturn("Hallo Welt".getBytes());
            when(message.getTypeClazz()).then(invocation -> String.class);
            when(serializer.fromByteArray(any(), any())).thenThrow(IOException.class);
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", serializer));
            when(config.getSerializationsBindingsInbound()).thenReturn(Map.of(String.class, "string"));

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();
            }
        }
    }

    @Nested
    class OnOutboundMessage {
        @Test
        void shouldSerializeMessageIfForConcreteClassSerializerExist(@Mock final CompressedPublicKey address,
                                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
            when(message.getContent()).thenReturn("Hallo Welt");
            when(config.getSerializationSerializers()).thenReturn(Map.of("string", new StringSerializer()));
            when(config.getSerializationsBindingsOutbound()).thenReturn(Map.of(String.class, "string"));

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, message);

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof SerializedApplicationMessage);
            }
        }

        @Test
        void shouldSerializeMessageIfForSuperClassSerializerExist(@Mock final CompressedPublicKey address,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Serializer serializer) throws IOException {
            when(message.getContent()).thenReturn("Hallo Welt");
            when(config.getSerializationSerializers()).thenReturn(Map.of("object", serializer));
            when(config.getSerializationsBindingsOutbound()).thenReturn(Map.of(Object.class, "object"));
            when(serializer.toByteArray(any())).thenReturn(new byte[0]);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, message);

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(m -> m instanceof SerializedApplicationMessage);
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final CompressedPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) throws InterruptedException {
            when(message.getContent()).thenReturn("Hallo Welt");

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());
                outboundMessages.await(1, SECONDS);
                outboundMessages.assertNoValues();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializationFail(@Mock final CompressedPublicKey recipient,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message,
                                                            @Mock final Serializer serializer) throws IOException, InterruptedException {
            when(message.getContent()).thenReturn("Hallo Welt");
            when(serializer.toByteArray(any())).thenThrow(IOException.class);
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
        void shouldBeAbleToSerializeNullMessage(@Mock final CompressedPublicKey address,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
            when(message.getContent()).thenReturn(null);

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, MessageSerializer.INSTANCE)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, message);

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new SerializedApplicationMessage(message.getSender(), message.getRecipient(), null, new byte[0]));
            }
        }
    }
}
