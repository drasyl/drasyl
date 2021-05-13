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

import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.handler.codec.MessageToMessageCodec;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.List;

/**
 * This handler serializes messages to {@link RemoteEnvelope <Application>} an vice vera.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public final class MessageSerializer extends MessageToMessageCodec<RemoteEnvelope<Application>, Object, IdentityPublicKey> {
    public static final MessageSerializer INSTANCE = new MessageSerializer();
    private static final Logger LOG = LoggerFactory.getLogger(MessageSerializer.class);

    private MessageSerializer() {
        // singleton
    }

    @Override
    protected void decode(final HandlerContext ctx,
                          final IdentityPublicKey sender,
                          final RemoteEnvelope<Application> envelope,
                          final List<Object> out) throws Exception {
        final Application body = envelope.getBodyAndRelease();
        final Serializer serializer = ctx.inboundSerialization().findSerializerFor(body.getType());

        if (serializer != null) {
            final Object o = serializer.fromByteArray(body.getPayload().toByteArray(), body.getType());
            out.add(o);
            LOG.trace("Message has been deserialized to '{}'", () -> o);
        }
        else {
            LOG.warn("No serializer was found for type '{}'. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/", body::getType);
        }
    }

    @Override
    protected void encode(final HandlerContext ctx,
                          final IdentityPublicKey recipient,
                          final Object o,
                          final List<Object> out) throws Exception {
        final String type;
        if (o != null) {
            type = o.getClass().getName();
        }
        else {
            type = null;
        }

        final Serializer serializer = ctx.outboundSerialization().findSerializerFor(type);

        if (serializer != null) {
            final RemoteEnvelope<Application> envelope = RemoteEnvelope.application(ctx.config().getNetworkId(), ctx.identity().getIdentityPublicKey(), ctx.identity().getProofOfWork(), recipient, type, serializer.toByteArray(o));
            out.add(envelope);
            LOG.trace("Message has been serialized to '{}'", () -> envelope);
        }
        else {
            LOG.warn("No serializer was found for type '{}'. You can find more information regarding this here: https://docs.drasyl.org/configuration/serialization/", type);
        }
    }
}
