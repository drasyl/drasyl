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
package org.drasyl.pipeline.skeleton;

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.AddressHandlerAdapter;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerAdapter;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of inbound messages
 * and events.
 * <p>
 * For example here is an implementation which only handle inbound {@link ApplicationMessage}
 * messages and {@link MessageEvent} events.
 *
 * <pre>
 *     public class MessageEventHandler extends
 *             {@link SimpleInboundEventAwareHandler}&lt;{@link ApplicationMessage}, {@link MessageEvent},
 *             {@link CompressedPublicKey}&gt; {
 *
 *        {@code @Override}
 *         protected void matchedEventTriggered({@link HandlerContext} ctx,
 *             {@link MessageEvent} event) {
 *             System.out.println(event);
 *         }
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
public abstract class SimpleInboundEventAwareHandler<I, E, A extends Address> extends AddressHandlerAdapter<A> {
    private final TypeParameterMatcher matcherMessage;
    private final TypeParameterMatcher matcherEvent;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected SimpleInboundEventAwareHandler() {
        matcherMessage = TypeParameterMatcher.find(this, SimpleInboundEventAwareHandler.class, "I");
        matcherEvent = TypeParameterMatcher.find(this, SimpleInboundEventAwareHandler.class, "E");
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType the type of messages to match
     * @param inboundEventType   the type of events to match
     * @param addressType        the type of the address to match
     */
    protected SimpleInboundEventAwareHandler(final Class<? extends I> inboundMessageType,
                                             final Class<? extends E> inboundEventType,
                                             final Class<? extends A> addressType) {
        super(addressType);
        matcherMessage = TypeParameterMatcher.get(inboundMessageType);
        matcherEvent = TypeParameterMatcher.get(inboundEventType);
    }

    @Override
    public void read(final HandlerContext ctx,
                     final Address sender,
                     final Object msg,
                     final CompletableFuture<Void> future) {
        if (acceptInbound(msg) && acceptAddress(sender)) {
            @SuppressWarnings("unchecked") final I castedMsg = (I) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) sender;
            matchedRead(ctx, castedAddress, castedMsg, future);
        }
        else {
            ctx.fireRead(sender, msg, future);
        }
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (acceptEvent(event)) {
            @SuppressWarnings("unchecked") final E castedEvent = (E) event;
            matchedEventTriggered(ctx, castedEvent, future);
        }
        else {
            ctx.fireEventTriggered(event, future);
        }
    }

    /**
     * Returns {@code true} if the given event should be handled. If {@code false} it will be passed
     * to the next {@link Handler} in the {@link Pipeline}.
     */
    protected boolean acceptEvent(final Event msg) {
        return matcherEvent.match(msg);
    }

    /**
     * Is called for each event of type {@link E}.
     *
     * @param ctx    handler context
     * @param event  the event
     * @param future the future of the message
     */
    protected abstract void matchedEventTriggered(HandlerContext ctx,
                                                  E event,
                                                  CompletableFuture<Void> future);

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link Handler} in the {@link Pipeline}.
     */
    protected boolean acceptInbound(final Object msg) {
        return matcherMessage.match(msg);
    }

    /**
     * Is called for each message of type {@link I}.
     *
     * @param ctx    handler context
     * @param sender the sender of the message
     * @param msg    the message
     * @param future the future of the message
     */
    protected abstract void matchedRead(HandlerContext ctx,
                                        A sender,
                                        I msg,
                                        CompletableFuture<Void> future);
}
