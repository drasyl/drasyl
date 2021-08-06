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
package org.drasyl.pipeline.skeleton;

import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of inbound messages
 * and events.
 * <p>
 * For example here is an implementation which only handle inbound {@code MyMessage} messages and
 * {@link MessageEvent} events.
 *
 * <pre>
 *     public class MessageEventHandler extends
 *             {@link SimpleInboundEventAwareHandler}&lt;{@code MyMessage}, {@link MessageEvent},
 *             {@link IdentityPublicKey}&gt; {
 *
 *        {@code @Override}
 *         protected void matchedEvent({@link HandlerContext} ctx,
 *             {@link MessageEvent} event) {
 *             System.out.println(event);
 *         }
 *
 *        {@code @Override}
 *         protected void matchedInbound({@link HandlerContext} ctx,
 *             {@link IdentityPublicKey} sender, {@code MyMessage} msg,
 *             {@link CompletableFuture}&lt;{@link Void}&gt; future) {
 *             System.out.println(msg);
 *         }
 *     }
 * </pre>
 */
@SuppressWarnings("java:S118")
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
    public void onInbound(final MigrationHandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        if (acceptInbound(msg) && acceptAddress(sender)) {
            @SuppressWarnings("unchecked") final I castedMsg = (I) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) sender;
            matchedInbound(ctx, castedAddress, castedMsg, future);
        }
        else {
            ctx.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
        }
    }

    @Override
    public void onEvent(final MigrationHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (acceptEvent(event)) {
            @SuppressWarnings("unchecked") final E castedEvent = (E) event;
            matchedEvent(ctx, castedEvent, future);
        }
        else {
            ctx.passEvent(event, future);
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
    protected abstract void matchedEvent(MigrationHandlerContext ctx,
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
    @SuppressWarnings("java:S112")
    protected abstract void matchedInbound(MigrationHandlerContext ctx,
                                           A sender,
                                           I msg,
                                           CompletableFuture<Void> future) throws Exception;
}
