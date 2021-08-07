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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.Null.NULL;

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
public abstract class SimpleInboundEventAwareHandler<I, E, A extends Address> implements ChannelInboundHandler {
    private final TypeParameterMatcher matcherMessage;
    private final TypeParameterMatcher matcherEvent;
    private final TypeParameterMatcher matcherAddress;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected SimpleInboundEventAwareHandler() {
        matcherMessage = TypeParameterMatcher.find(this, SimpleInboundEventAwareHandler.class, "I");
        matcherEvent = TypeParameterMatcher.find(this, SimpleInboundEventAwareHandler.class, "E");
        matcherAddress = TypeParameterMatcher.find(this, SimpleInboundEventAwareHandler.class, "A");
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
        matcherMessage = TypeParameterMatcher.get(inboundMessageType);
        matcherEvent = TypeParameterMatcher.get(inboundEventType);
        matcherAddress = TypeParameterMatcher.get(addressType);
    }

    @org.drasyl.pipeline.Skip
    @SuppressWarnings("java:S112")
    public void onInbound(final ChannelHandlerContext ctx,
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

    @org.drasyl.pipeline.Skip
    public void onEvent(final ChannelHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        if (acceptEvent(event)) {
            @SuppressWarnings("unchecked") final E castedEvent = (E) event;
            matchedEvent(ctx, castedEvent, future);
        }
        else {
            ctx.fireUserEventTriggered(new MigrationEvent(event, future));
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
    protected abstract void matchedEvent(ChannelHandlerContext ctx,
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
    protected abstract void matchedInbound(ChannelHandlerContext ctx,
                                           A sender,
                                           I msg,
                                           CompletableFuture<Void> future) throws Exception;

    /**
     * Returns {@code true} if the given address should be handled, {@code false} otherwise.
     */
    protected boolean acceptAddress(final Address address) {
        return matcherAddress.match(address);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof MigrationInboundMessage) {
            final MigrationInboundMessage<?, ?> migrationMsg = (MigrationInboundMessage<?, ?>) msg;
            final Object payload = migrationMsg.message() == NULL ? null : migrationMsg.message();
            try {
                onInbound(ctx, migrationMsg.address(), payload, migrationMsg.future());
            }
            catch (final Exception e) {
                migrationMsg.future().completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        if (evt instanceof MigrationEvent) {
            onEvent(ctx, ((MigrationEvent) evt).event(), ((MigrationEvent) evt).future());
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx,
                                final Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
        ctx.fireChannelWritabilityChanged();
    }
}
