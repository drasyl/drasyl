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
package org.drasyl.pipeline.skeleton;

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of messages.
 * <p>
 * For example here is an implementation which only handle {@code MyMessage}s of type {@code
 * MyMessage}.
 *
 * <pre>
 *     public class ChunkedHandler extends
 *             {@link SimpleOutboundHandler}&lt;{@code MyMessage}, {@link CompressedPublicKey}&gt; {
 *
 *        {@code @Override}
 *         protected void matchedOutbound({@link HandlerContext} ctx,
 *             {@link CompressedPublicKey} recipient, {@code MyMessage} msg,
 *             {@link CompletableFuture}&lt;{@link Void}&gt; future) {
 *             System.out.println(msg);
 *         }
 *     }
 * </pre>
 */
@SuppressWarnings("java:S118")
public abstract class SimpleOutboundHandler<O, A extends Address> extends AddressHandlerAdapter<A> {
    private final TypeParameterMatcher matcherMessage;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected SimpleOutboundHandler() {
        matcherMessage = TypeParameterMatcher.find(this, SimpleOutboundHandler.class, "O");
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType the type of messages to match
     * @param addressType         the type of the address to match
     */
    protected SimpleOutboundHandler(final Class<? extends O> outboundMessageType,
                                    final Class<? extends A> addressType) {
        super(addressType);
        matcherMessage = TypeParameterMatcher.get(outboundMessageType);
    }

    @Override
    public void onOutbound(final HandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        if (acceptOutbound(msg) && acceptAddress(recipient)) {
            @SuppressWarnings("unchecked") final O castedMsg = (O) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) recipient;
            matchedOutbound(ctx, castedAddress, castedMsg, future);
        }
        else {
            ctx.passOutbound(recipient, msg, future);
        }
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link Handler} in the {@link Pipeline}.
     */
    protected boolean acceptOutbound(final Object msg) {
        return matcherMessage.match(msg);
    }

    /**
     * Is called for each message of type {@link O}.
     *
     * @param ctx       handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    a future for the message
     */
    @SuppressWarnings("java:S112")
    protected abstract void matchedOutbound(HandlerContext ctx,
                                            A recipient,
                                            O msg,
                                            CompletableFuture<Void> future) throws Exception;
}
