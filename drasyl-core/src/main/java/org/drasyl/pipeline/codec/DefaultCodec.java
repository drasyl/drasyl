/*
 * Copyright (c) 2021.
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
package org.drasyl.pipeline.codec;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.serialization.ByteArraySerializer;
import org.drasyl.serialization.JacksonJsonSerializer;
import org.drasyl.serialization.Serializer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * This default codec allows to encode/decode all supported objects by Jackson.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public class DefaultCodec extends SimpleDuplexHandler<ApplicationMessage, Object, Address> {
    public static final DefaultCodec INSTANCE = new DefaultCodec();
    public static final String DEFAULT_CODEC = "defaultCodec";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCodec.class);

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ApplicationMessage msg,
                               final CompletableFuture<Void> future) {
        try {
            final Serializer serializer;
            if (byte[].class == msg.getTypeClazz()) {
                serializer = new ByteArraySerializer();
            }
            else if (ctx.inboundValidator().validate(msg.getTypeClazz())) {
                serializer = new JacksonJsonSerializer();
            }
            else {
                serializer = null;
            }

            if (serializer != null) {
                final Object decodedMessage = serializer.fromByteArray(msg.getContent(), msg.getTypeClazz());
                ctx.fireRead(msg.getSender(), decodedMessage, future);
                LOG.trace("[{}]: Decoded Message '{}'", ctx::name, () -> decodedMessage);
            }
            else {
                // can't decode, pass message to the next handler in the pipeline
                ctx.fireRead(sender, msg, future);
            }
        }
        catch (final IOException | IllegalArgumentException | ClassNotFoundException e) {
            LOG.warn("[{}]: Unable to deserialize '{}': ", ctx::name, () -> msg, () -> e);
            ctx.fireRead(sender, msg, future);
        }
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final Object msg,
                                final CompletableFuture<Void> future) {
        try {
            final Serializer serializer;
            if (msg instanceof byte[]) {
                serializer = new ByteArraySerializer();
            }
            else if (ctx.outboundValidator().validate(msg.getClass())) {
                serializer = new JacksonJsonSerializer();
            }
            else {
                serializer = null;
            }

            if (serializer != null && recipient instanceof CompressedPublicKey) {
                final byte[] bytes = serializer.toByteArray(msg);
                ctx.write(recipient, new ApplicationMessage(ctx.identity().getPublicKey(), (CompressedPublicKey) recipient, msg.getClass(), bytes), future);
                LOG.trace("[{}]: Encoded Message '{}'", ctx::name, () -> msg);
            }
            else {
                // can't encode, pass message to the next handler in the pipeline
                ctx.write(recipient, msg, future);
            }
        }
        catch (final IOException e) {
            LOG.warn("[{}]: Unable to serialize '{}': ", ctx::name, () -> msg, () -> e);
            ctx.write(recipient, msg, future);
        }
    }
}
