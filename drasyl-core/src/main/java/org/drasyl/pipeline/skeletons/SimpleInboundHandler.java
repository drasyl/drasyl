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
package org.drasyl.pipeline.skeletons;

import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.HandlerAdapter;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of inbound messages.
 * <p>
 * For example here is an implementation which only handle inbound {@link ApplicationMessage}
 * messages.
 *
 * <pre>
 *     public class MessageEventHandler extends
 *             {@link SimpleInboundHandler}&lt;{@link ApplicationMessage}, {@link CompressedPublicKey}&gt; {
 *
 *        {@code @Override}
 *         protected void matchedRead({@link HandlerContext} ctx,
 *             {@link CompressedPublicKey} sender, {@link ApplicationMessage} msg,
 *             {@link CompletableFuture}&lt;{@link Void}&gt; future) {
 *             System.out.println(msg);
 *         }
 *     }
 * </pre>
 */
public abstract class SimpleInboundHandler<I, A extends Address> extends SimpleInboundEventAwareHandler<I, Event, A> {
    protected SimpleInboundHandler() {
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType the type of messages to match
     * @param addressType        the type of the address to match
     */
    protected SimpleInboundHandler(final Class<? extends I> inboundMessageType,
                                   final Class<? extends A> addressType) {
        super(inboundMessageType, Event.class, addressType);
    }

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final Event event,
                                         final CompletableFuture<Void> future) {
        ctx.fireEventTriggered(event, future);
    }
}