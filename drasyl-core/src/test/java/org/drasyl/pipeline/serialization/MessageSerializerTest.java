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
package org.drasyl.pipeline.serialization;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.serialization.Serializer;
import org.drasyl.serialization.StringSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.drasyl.channel.EmbeddedDrasylServerChannel.NULL_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageSerializerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Nested
    class OnInboundMessage {
        @Test
        void shouldDeserializeMessageIfSerializerForConcreteClassExist(@Mock final IdentityPublicKey address) {
            when(config.getSerializationSerializers()).thenReturn(ImmutableMap.of("string", new StringSerializer()));
            when(config.getSerializationsBindingsInbound()).thenReturn(ImmutableMap.of(String.class, "string"));
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> inboundMessages = pipeline.drasylInboundMessages().test();

                pipeline.processInbound(address, message);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue("Hallo Welt");
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldDeserializeMessageIfSerializerForSuperClassExist(@Mock final IdentityPublicKey address,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Serializer serializer) throws IOException {
            when(config.getSerializationSerializers()).thenReturn(ImmutableMap.of("object", serializer));
            when(config.getSerializationsBindingsInbound()).thenReturn(ImmutableMap.of(Object.class, "object"));
            when(serializer.fromByteArray(any(), anyString())).thenReturn("Hallo Welt");
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> inboundMessages = pipeline.drasylInboundMessages().test();

                pipeline.processInbound(address, message);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue("Hallo Welt");
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldBeAbleToDeserializeNullMessage(@Mock final IdentityPublicKey address) {
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), null, null);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> inboundMessages = pipeline.drasylInboundMessages().test();

                pipeline.processInbound(address, message);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(NULL_MESSAGE);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final IdentityPublicKey sender) throws InterruptedException {
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> inboundMessages = pipeline.drasylInboundMessages().test();

                pipeline.processInbound(sender, message);

                inboundMessages.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfDeserializationFail(@Mock final IdentityPublicKey sender,
                                                              @Mock final Serializer serializer) throws IOException, InterruptedException {
            when(serializer.fromByteArray(any(), anyString())).thenThrow(IOException.class);
            when(config.getSerializationSerializers()).thenReturn(ImmutableMap.of("string", serializer));
            when(config.getSerializationsBindingsInbound()).thenReturn(ImmutableMap.of(String.class, "string"));
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> inboundMessages = pipeline.drasylInboundMessages().test();

                pipeline.processInbound(sender, message);

                inboundMessages.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class OnOutboundMessage {
        @Test
        void shouldSerializeMessageIfForConcreteClassSerializerExist() {
            when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());
            when(config.getSerializationSerializers()).thenReturn(ImmutableMap.of("string", new StringSerializer()));
            when(config.getSerializationsBindingsOutbound()).thenReturn(ImmutableMap.of(String.class, "string"));

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<ApplicationMessage> outboundMessages = pipeline.drasylOutboundMessages(ApplicationMessage.class).test();

                pipeline.processOutbound(identity.getIdentityPublicKey(), "Hello World").join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldSerializeMessageIfForSuperClassSerializerExist(@Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Serializer serializer) throws IOException {
            when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());
            when(config.getSerializationSerializers()).thenReturn(ImmutableMap.of("object", serializer));
            when(config.getSerializationsBindingsOutbound()).thenReturn(ImmutableMap.of(Object.class, "object"));
            when(serializer.toByteArray(any())).thenReturn(new byte[0]);

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<ApplicationMessage> outboundMessages = pipeline.drasylOutboundMessages(ApplicationMessage.class).test();

                pipeline.processOutbound(identity.getIdentityPublicKey(), message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final IdentityPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Object message) {
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> outboundMessages = pipeline.drasylOutboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());

                outboundMessages
                        .assertNoValues();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializationFail(@Mock final IdentityPublicKey recipient,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                            @Mock final Serializer serializer) throws InterruptedException {
            when(config.getSerializationSerializers()).thenReturn(ImmutableMap.of("string", serializer));
            when(config.getSerializationsBindingsOutbound()).thenReturn(ImmutableMap.of(String.class, "string"));

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<Object> outboundMessages = pipeline.drasylOutboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, message).get());

                outboundMessages.assertNoValues();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldBeAbleToSerializeNullMessage() {
            when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());

            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, MessageSerializer.INSTANCE);
            try {
                final TestObserver<ApplicationMessage> outboundMessages = pipeline.drasylOutboundMessages(ApplicationMessage.class).test();

                pipeline.processOutbound(identity.getIdentityPublicKey(), null);

                outboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
