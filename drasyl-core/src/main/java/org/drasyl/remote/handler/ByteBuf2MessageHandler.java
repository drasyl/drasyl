/*
 * Copyright (c) 2020.
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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.message.AcknowledgementMessage;
import org.drasyl.remote.message.DiscoverMessage;
import org.drasyl.remote.message.RemoteApplicationMessage;
import org.drasyl.remote.message.RemoteMessage;
import org.drasyl.remote.message.UniteMessage;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.remote.protocol.Protocol.Acknowledgement;
import static org.drasyl.remote.protocol.Protocol.Application;
import static org.drasyl.remote.protocol.Protocol.Discovery;
import static org.drasyl.remote.protocol.Protocol.Unite;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * Handler that converts a given {@link ByteBuf} to a {@link RemoteMessage} or {@link
 * IntermediateEnvelope}.
 */
public class ByteBuf2MessageHandler extends SimpleInboundHandler<ByteBuf, Address> {
    public static final ByteBuf2MessageHandler INSTANCE = new ByteBuf2MessageHandler();
    public static final String BYTE_BUF_2_MESSAGE_HANDLER = "BYTE_BUF_2_MESSAGE_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(ByteBuf2MessageHandler.class);

    private ByteBuf2MessageHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ByteBuf byteBuf,
                               final CompletableFuture<Void> future) {
        try {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(byteBuf);

            // This message is for us and we will fully decode it
            if (envelope.getRecipient().equals(ctx.identity().getPublicKey())) {
                try {
                    final RemoteMessage<? extends MessageLite> remoteMessage;

                    switch (envelope.getPrivateHeader().getType()) {
                        case ACKNOWLEDGEMENT:
                            remoteMessage = new AcknowledgementMessage(envelope.getPublicHeader(), (Acknowledgement) envelope.getBody());
                            break;
                        case APPLICATION:
                            remoteMessage = new RemoteApplicationMessage(envelope.getPublicHeader(), (Application) envelope.getBody());
                            break;
                        case DISCOVERY:
                            remoteMessage = new DiscoverMessage(envelope.getPublicHeader(), (Discovery) envelope.getBody());
                            break;
                        case UNITE:
                            remoteMessage = new UniteMessage(envelope.getPublicHeader(), (Unite) envelope.getBody());
                            break;
                        default:
                            throw new IllegalArgumentException("Message is not of any known type.");
                    }

                    ctx.fireRead(sender, remoteMessage, future);
                }
                finally {
                    if (byteBuf.refCnt() != 0) {
                        byteBuf.release();
                    }
                }
            }
            else {
                // Just forward the message to the recipient
                ctx.fireRead(sender, envelope, future);
            }
        }
        catch (final Exception e) {
            LOG.warn("Unable to deserialize '{}': {}", sanitizeLogArg(byteBuf), e);
            future.completeExceptionally(new Exception("Message could not be deserialized.", e));
            if (byteBuf.refCnt() != 0) {
                byteBuf.release();
            }
        }
    }
}
