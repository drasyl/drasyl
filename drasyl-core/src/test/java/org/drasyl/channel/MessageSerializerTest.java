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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.MessageSerializerProtocol.SerializedPayload;
import org.drasyl.identity.Identity;
import org.drasyl.serialization.StringSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.drasyl.channel.Null.NULL;
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

    @Nested
    class OnInboundMessage {
        @Test
        void shouldDeserializeMessageIfSerializerForConcreteClassExist(@Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(inboundSerialization.findSerializerFor(String.class.getName()).fromByteArray(any(), eq(String.class.getName()))).thenReturn("Hallo Welt");

            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final ByteBuf payload = Unpooled.buffer();
            try (final ByteBufOutputStream out = new ByteBufOutputStream(payload)) {
                SerializedPayload.newBuilder().setType(String.class.getName()).setPayload(ByteString.copyFromUtf8("Hallo Welt")).build().writeDelimitedTo(out);
            }
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(payload);

                assertEquals("Hallo Welt", channel.readInbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldBeAbleToDeserializeNullMessage(@Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final ByteBuf payload = Unpooled.buffer();
            try (final ByteBufOutputStream out = new ByteBufOutputStream(payload)) {
                SerializedPayload.newBuilder().build().writeDelimitedTo(out);
            }
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(payload);

                assertEquals(NULL, channel.readInbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(inboundSerialization.findSerializerFor(anyString())).thenReturn(null);

            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final ByteBuf payload = Unpooled.buffer();
            try (final ByteBufOutputStream out = new ByteBufOutputStream(payload)) {
                SerializedPayload.newBuilder().setType(String.class.getName()).setPayload(ByteString.copyFromUtf8("Hallo Welt")).build().writeDelimitedTo(out);
            }
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(payload);

                assertNull(channel.readInbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfDeserializationFail(@Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) throws IOException {
            when(inboundSerialization.findSerializerFor(anyString()).fromByteArray(any(), anyString())).thenThrow(IOException.class);

            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final ByteBuf payload = Unpooled.buffer();
            try (final ByteBufOutputStream out = new ByteBufOutputStream(payload)) {
                SerializedPayload.newBuilder().setType(String.class.getName()).setPayload(ByteString.copyFromUtf8("Hallo Welt")).build().writeDelimitedTo(out);
            }
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(payload);

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

            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush("Hello World");

                final ByteBuf actual = channel.readOutbound();
                assertThat(actual, instanceOf(ByteBuf.class));

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializerDoesNotExist(@Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(message);
                assertFalse(promise.isSuccess());

                assertNull(channel.readOutbound());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyIfSerializationFail(@Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(message);

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

            final MessageSerializer handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(NULL);

                final ByteBuf actual = channel.readOutbound();
                assertThat(actual, instanceOf(ByteBuf.class));

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldPassThroughByteBuf(
                @Mock(answer = RETURNS_DEEP_STUBS) final Serialization inboundSerialization,
                @Mock(answer = RETURNS_DEEP_STUBS) final Serialization outboundSerialization) {
            final ChannelHandler handler = new MessageSerializer(inboundSerialization, outboundSerialization);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ByteBuf msg = Unpooled.copiedBuffer("Hello", UTF_8);
            channel.writeAndFlush(msg);

            assertEquals(msg, channel.readOutbound());

            msg.release();
        }
    }
}
