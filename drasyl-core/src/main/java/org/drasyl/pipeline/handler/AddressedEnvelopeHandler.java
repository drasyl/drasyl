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
package org.drasyl.pipeline.handler;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;

import java.util.concurrent.CompletableFuture;

/**
 * This handler wraps all outgoing messages in an {@link AddressedEnvelope}. All incoming messages
 * of the {@link AddressedEnvelope} type are unwrapped.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public final class AddressedEnvelopeHandler extends SimpleDuplexHandler<AddressedEnvelope<?, ?>, Object, CompressedPublicKey> {
    public static final String ADDRESSED_ENVELOPE_HANDLER = "ADDRESSED_ENVELOPE_HANDLER";
    public static final Handler INSTANCE = new AddressedEnvelopeHandler();

    private AddressedEnvelopeHandler() {
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final CompressedPublicKey recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
        // use recipient of the pipeline as recipient of the envelope
        // add own public key as sender
        ctx.passOutbound(recipient, new ApplicationMessage(ctx.identity().getPublicKey(), recipient, msg), future);
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final CompressedPublicKey sender,
                                  final AddressedEnvelope<?, ?> msg,
                                  final CompletableFuture<Void> future) {
        // use sender of the envelope as sender of the pipeline
        ctx.passInbound(msg.getSender(), msg.getContent(), future);
    }
}
