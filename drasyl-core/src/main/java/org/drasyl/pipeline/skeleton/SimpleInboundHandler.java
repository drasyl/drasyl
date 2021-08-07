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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.event.Event;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.channel.Null.NULL;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of inbound messages.
 * <p>
 * For example here is an implementation which only handle inbound {@code MyMessage} messages.
 *
 * <pre>
 *     public class MessageEventHandler extends
 *             {@link SimpleInboundHandler}&lt;{@code MyMessage}, {@link IdentityPublicKey}&gt; {
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
public abstract class SimpleInboundHandler<MI, MA extends Address> extends SimpleChannelInboundHandler<MigrationInboundMessage<MI, MA>> {
    private final TypeParameterMatcher matcherMessage;
    private final TypeParameterMatcher matcherAddress;

    protected SimpleInboundHandler() {
        super(false);
        this.matcherMessage = TypeParameterMatcher.find(this, SimpleInboundHandler.class, "MI");
        this.matcherAddress = TypeParameterMatcher.find(this, SimpleInboundHandler.class, "MA");
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType the type of messages to match
     * @param addressType        the type of the address to match
     */
    protected SimpleInboundHandler(final Class<? extends MI> inboundMessageType,
                                   final Class<? extends MA> addressType) {
        super(false);
        this.matcherMessage = TypeParameterMatcher.get(inboundMessageType);
        this.matcherAddress = TypeParameterMatcher.get(addressType);
    }

    @Skip
    public void onEvent(final ChannelHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));
    }

    @Skip
    @SuppressWarnings("java:S112")
    public void onInbound(final ChannelHandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) throws Exception {
        if (acceptInbound(msg) && acceptAddress(sender)) {
            @SuppressWarnings("unchecked") final MI castedMsg = (MI) msg;
            @SuppressWarnings("unchecked") final MA castedAddress = (MA) sender;
            matchedInbound(ctx, castedAddress, castedMsg, future);
        }
        else {
            ctx.fireChannelRead(new MigrationInboundMessage<>(msg, sender, future));
        }
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be
     * passed to the next {@link Handler} in the {@link Pipeline}.
     */
    protected boolean acceptInbound(final Object msg) {
        return matcherMessage.match(msg);
    }

    /**
     * Is called for each message of type {@link MI}.
     *
     * @param ctx    handler context
     * @param sender the sender of the message
     * @param msg    the message
     * @param future the future of the message
     */
    @SuppressWarnings("java:S112")
    protected abstract void matchedInbound(ChannelHandlerContext ctx,
                                           MA sender,
                                           MI msg,
                                           CompletableFuture<Void> future) throws Exception;

    /**
     * Returns {@code true} if the given address should be handled, {@code false} otherwise.
     */
    protected boolean acceptAddress(final Address address) {
        return matcherAddress.match(address);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final MigrationInboundMessage<MI, MA> msg) {
        final Object payload = msg.message() == NULL ? null : msg.message();
        try {
            onInbound(ctx, msg.address(), payload, msg.future());
        }
        catch (final Exception e) {
            msg.future().completeExceptionally(e);
            ctx.fireExceptionCaught(e);
            ReferenceCountUtil.safeRelease(msg.message());
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
}
