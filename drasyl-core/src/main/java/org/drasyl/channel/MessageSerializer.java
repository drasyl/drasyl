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
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.MessageSerializerProtocol.SerializedPayload;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.drasyl.channel.Null.NULL;

/**
 * This handler serializes messages to {@link ApplicationMessage} an vice vera.
 */
@SuppressWarnings({ "java:S110" })
public final class MessageSerializer extends MessageToMessageCodec<ByteBuf, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageSerializer.class);
    private final Serialization inboundSerialization;
    private final Serialization outboundSerialization;

    public MessageSerializer(final Serialization inboundSerialization,
                             final Serialization outboundSerialization) {
        this.inboundSerialization = requireNonNull(inboundSerialization);
        this.outboundSerialization = requireNonNull(outboundSerialization);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          Object o,
                          final List<Object> out) {
        if (o == NULL) {
            o = null;
        }

        final String type;
        if (o != null) {
            type = o.getClass().getName();
        }
        else {
            type = null;
        }

        final Serializer serializer = outboundSerialization.findSerializerFor(type);

        if (serializer != null) {
            try {
                final ByteString payload = ByteString.copyFrom(serializer.toByteArray(o));

                final SerializedPayload.Builder builder = SerializedPayload.newBuilder();

                if (type != null) {
                    builder.setType(type);
                }
                builder.setPayload(payload);

                final ByteBuf bytes = ctx.alloc().ioBuffer().writeBytes(builder.build().toByteArray());
                out.add(bytes);
                LOG.trace("Message has been serialized to `{}`", () -> bytes);
            }
            catch (final IOException e) {
                throw new EncoderException("Serialization failed", e);
            }
        }
        else {
            throw new EncoderException("No serializer was found for type `" + type + "`. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/");
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf bytes,
                          final List<Object> out) {
        try {
            final byte[] byteArray = ByteBufUtil.getBytes(bytes);
            final SerializedPayload serializedPayload = SerializedPayload.parseFrom(byteArray);
            final String type = serializedPayload.getType();
            final byte[] payload = serializedPayload.getPayload().toByteArray();
            final Serializer serializer = inboundSerialization.findSerializerFor(type);

            if (serializer != null) {
                final Object o = serializer.fromByteArray(payload, type);

                if (o == null) {
                    out.add(NULL);
                    LOG.trace("Message has been deserialized to `{}`", () -> NULL);
                }
                else {
                    out.add(o);
                    LOG.trace("Message has been deserialized to `{}`", () -> o);
                }
            }
            else {
                throw new DecoderException("No serializer was found for type `" + type + "`. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/");
            }
        }
        catch (final IOException e) {
            throw new DecoderException("Deserialization failed", e);
        }
    }
}
