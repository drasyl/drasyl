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
package org.drasyl.channel;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.serialization.Serializer;
import org.drasyl.serialization.StringSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.io.IOException;
import java.net.SocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageSerializerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    private final int networkId = 0;

    @Nested
    class OnInboundMessage {
        @Test
        void shouldDeserializeMessageIfSerializerForConcreteClassExist(@Mock final IdentityPublicKey address,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(inboundSerialization.findSerializerFor(String.class.getName()).fromByteArray(any(), eq(String.class.getName()))).thenReturn("Hallo Welt");

            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new AddressedMessage<>(message, address));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new AddressedMessage<>("Hallo Welt", address), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldBeAbleToDeserializeNullMessage(@Mock final IdentityPublicKey address,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(inboundSerialization.findSerializerFor(null).fromByteArray(any(), eq((String) null))).thenReturn(null);

            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), null, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new AddressedMessage<>(message, address));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new AddressedMessage<>(null, address), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final IdentityPublicKey sender,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            when(inboundSerialization.findSerializerFor(anyString())).thenReturn(null);

            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new AddressedMessage<>(message, sender));

                assertNull(channel.readInbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfDeserializationFail(@Mock final IdentityPublicKey sender,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(inboundSerialization.findSerializerFor(anyString()).fromByteArray(any(), anyString())).thenThrow(IOException.class);

            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final ApplicationMessage message = ApplicationMessage.of(1, IdentityTestUtil.ID_1.getIdentityPublicKey(), IdentityTestUtil.ID_1.getProofOfWork(), IdentityTestUtil.ID_2.getIdentityPublicKey(), String.class.getName(), ByteString.copyFromUtf8("Hallo Welt"));
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new AddressedMessage<>(message, sender));

                assertNull(channel.readInbound());
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class OnOutboundMessage {
        @Test
        void shouldSerializeMessageIfForConcreteClassSerializerExist(
                @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getAddress()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());
            when(outboundSerialization.findSerializerFor(String.class.getName())).thenReturn(new StringSerializer());

            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new AddressedMessage<>("Hello World", identity.getIdentityPublicKey()));

                final AddressedMessage<RemoteMessage, SocketAddress> actual = channel.readOutbound();
                assertThat(actual.message(), instanceOf(ApplicationMessage.class));

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock final IdentityPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(new AddressedMessage<>(message, recipient), promise);
                assertFalse(promise.isSuccess());

                assertNull(channel.readOutbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializationFail(@Mock final IdentityPublicKey recipient,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                            @Mock final Serializer serializer,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(new AddressedMessage<>(message, recipient), promise);

                assertFalse(promise.isSuccess());
                assertNull(channel.readOutbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldBeAbleToSerializeNullMessage(
                @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getAddress()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(IdentityTestUtil.ID_1.getProofOfWork());
            when(outboundSerialization.findSerializerFor(null).toByteArray(null)).thenReturn(new byte[0]);

            final MessageSerializer handler = new MessageSerializer(networkId, identity.getAddress(), identity.getProofOfWork(), inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new AddressedMessage<>(null, identity.getIdentityPublicKey()));

                final AddressedMessage<RemoteMessage, SocketAddress> actual = channel.readOutbound();
                assertThat(actual.message(), instanceOf(ApplicationMessage.class));

                actual.release();
            }
            finally {
                channel.close();
            }
        }
    }
}
