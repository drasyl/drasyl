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
package org.drasyl.pipeline;

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.ApplicationMessage;

/**
 * {@link InboundHandlerAdapter} which allows to explicit only handle a specific type of messages
 * and events.
 * <p>
 * For example here is an implementation which only handle {@link MessageEvent} events.
 *
 * <pre>
 *     public class MessageEventHandler extends
 *             {@link SimpleInboundHandler}&lt;{@link ApplicationMessage}, {@link MessageEvent}&gt; {
 *
 *         {@code @Override}
 *         protected void matchedEventTriggered({@link HandlerContext} ctx, {@link MessageEvent} event) {
 *             System.out.println(event);
 *         }
 *     }
 * </pre>
 */
public abstract class SimpleInboundHandler<I, E> extends InboundHandlerAdapter {
    private final TypeParameterMatcher matcherMessage;
    private final TypeParameterMatcher matcherEvent;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected SimpleInboundHandler() {
        matcherMessage = TypeParameterMatcher.find(this, SimpleInboundHandler.class, "I");
        matcherEvent = TypeParameterMatcher.find(this, SimpleInboundHandler.class, "E");
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType the type of messages to match
     * @param inboundEventType   the type of events to match
     */
    protected SimpleInboundHandler(Class<? extends I> inboundMessageType,
                                   Class<? extends E> inboundEventType) {
        matcherMessage = TypeParameterMatcher.get(inboundMessageType);
        matcherEvent = TypeParameterMatcher.get(inboundEventType);
    }

    @Override
    public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
        if (acceptInbound(msg)) {
            @SuppressWarnings("unchecked")
            I castedMsg = (I) msg;
            matchedRead(ctx, sender, castedMsg);
        }
        else {
            ctx.fireRead(sender, msg);
        }
    }

    @Override
    public void eventTriggered(HandlerContext ctx, Event event) {
        if (acceptEvent(event)) {
            @SuppressWarnings("unchecked")
            E castedEvent = (E) event;
            matchedEventTriggered(ctx, castedEvent);
        }
        else {
            ctx.fireEventTriggered(event);
        }
    }

    /**
     * Returns {@code true} if the given event should be handled. If {@code false} it will be passed
     * to the next {@link InboundHandler} in the {@link Pipeline}.
     */
    protected boolean acceptEvent(Event msg) {
        return matcherEvent.match(msg);
    }

    /**
     * Is called for each event of type {@link E}.
     *
     * @param ctx   handler context
     * @param event the event
     */
    protected abstract void matchedEventTriggered(HandlerContext ctx, E event);

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link InboundHandler} in the {@link Pipeline}.
     */
    protected boolean acceptInbound(Object msg) {
        return matcherMessage.match(msg);
    }

    /**
     * Is called for each message of type {@link I}.
     *
     * @param ctx    handler context
     * @param sender the sender of the message
     * @param msg    the message
     */
    protected abstract void matchedRead(HandlerContext ctx, CompressedPublicKey sender, I msg);
}
