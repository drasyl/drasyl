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

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.Stateless;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.INBOUND_SERIALIZATION_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.OUTBOUND_SERIALIZATION_ATTR_KEY;

/**
 * This handler serializes messages to {@link ApplicationMessage} an vice vera.
 */
@ChannelHandler.Sharable
@Stateless
@SuppressWarnings({ "java:S110" })
public final class MessageSerializer extends MessageToMessageCodec<AddressedMessage<?, ?>, AddressedMessage<?, ?>> {
    public static final MessageSerializer INSTANCE = new MessageSerializer();
    private static final Logger LOG = LoggerFactory.getLogger(MessageSerializer.class);

    private MessageSerializer() {
        // singleton
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws Exception {
        if (msg.address() instanceof IdentityPublicKey) {
            final Object o = msg.message();

            final String type;
            if (o != null) {
                type = o.getClass().getName();
            }
            else {
                type = null;
            }

            final Serializer serializer = ctx.attr(OUTBOUND_SERIALIZATION_ATTR_KEY).get().findSerializerFor(type);

            if (serializer != null) {
                final ApplicationMessage applicationMsg = ApplicationMessage.of(ctx.attr(CONFIG_ATTR_KEY).get().getNetworkId(), ctx.attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey(), ctx.attr(IDENTITY_ATTR_KEY).get().getProofOfWork(), (IdentityPublicKey) msg.address(), type, ByteString.copyFrom(serializer.toByteArray(o)));
                out.add(new AddressedMessage<>(applicationMsg, msg.address()));
                LOG.trace("Message has been serialized to `{}`", () -> applicationMsg);
            }
            else {
                LOG.warn("No serializer was found for type `{}`. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/", type);
            }
        }
        else {
            out.add(msg);
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedMessage<?, ?> msg,
                          final List<Object> out) throws Exception {
        if (msg.message() instanceof ApplicationMessage && msg.address() instanceof IdentityPublicKey) {
            final ApplicationMessage applicationMsg = (ApplicationMessage) msg.message();
            final Serializer serializer = ctx.attr(INBOUND_SERIALIZATION_ATTR_KEY).get().findSerializerFor(applicationMsg.getType());

            if (serializer != null) {
                final Object o = serializer.fromByteArray(applicationMsg.getPayloadAsByteArray(), applicationMsg.getType());
                out.add(new AddressedMessage<>(o, msg.address()));
                LOG.trace("Message has been deserialized to `{}`", () -> o);
            }
            else {
                LOG.warn("No serializer was found for type `{}`. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/", applicationMsg::getType);
            }
        }
        else {
            out.add(msg);
        }
    }
}
