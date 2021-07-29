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
package org.drasyl.codec;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

/**
 * This handler serializes messages to {@link ApplicationMessage} an vice vera.
 */
@ChannelHandler.Sharable
public class MessageSerializer extends MessageToMessageCodec<AddressedApplicationMessage, AddressedObject> {
    public static final MessageSerializer INSTANCE = new MessageSerializer();
    private static final Logger LOG = LoggerFactory.getLogger(MessageSerializer.class);

    @Override
    protected void encode(final ChannelHandlerContext ctx,
                          final AddressedObject addressedMsg,
                          final List<Object> out) throws Exception {
        final Object o = addressedMsg.content();
        final IdentityPublicKey recipient = addressedMsg.recipient();

        final String type;
        if (o != null) {
            type = o.getClass().getName();
        }
        else {
            type = null;
        }

        final Serializer serializer = ((DrasylServerChannel) ctx.channel()).outboundSerialization().findSerializerFor(type);

        if (serializer != null) {
            final ApplicationMessage message = ApplicationMessage.of(((DrasylServerChannel) ctx.channel()).drasylConfig().getNetworkId(), ((DrasylServerChannel) ctx.channel()).localAddress0().getIdentityPublicKey(), ((DrasylServerChannel) ctx.channel()).localAddress0().getProofOfWork(), recipient, type, ByteString.copyFrom(serializer.toByteArray(o)));
            out.add(new AddressedApplicationMessage(message, recipient, null));
            LOG.trace("Message has been serialized to `{}`", () -> message);
        }
        else {
            LOG.warn("No serializer was found for type `{}`. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/", type);
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final AddressedApplicationMessage addressedMsg,
                          final List<Object> out) throws Exception {
        final ApplicationMessage message = addressedMsg.content();
        final IdentityPublicKey sender = (IdentityPublicKey) addressedMsg.sender();

        final Serializer serializer = ((DrasylServerChannel) ctx.channel()).inboundSerialization().findSerializerFor(message.getType());

        if (serializer != null) {
            final Object o = serializer.fromByteArray(message.getPayloadAsByteArray(), message.getType());
            out.add(new AddressedObject(o, null, sender));
            LOG.trace("Message has been deserialized to `{}`", () -> o);
        }
        else {
            LOG.warn("No serializer was found for type `{}`. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/", message::getType);
        }
    }
}
