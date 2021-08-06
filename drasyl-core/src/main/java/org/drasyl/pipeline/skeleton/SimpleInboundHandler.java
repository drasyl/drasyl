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

import org.drasyl.channel.MigrationEvent;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.Event;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;

import java.util.concurrent.CompletableFuture;

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

    @Skip
    @Override
    public void onEvent(final MigrationHandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));
    }

    @Override
    protected void matchedEvent(final MigrationHandlerContext ctx,
                                final Event event,
                                final CompletableFuture<Void> future) {
        ctx.fireUserEventTriggered(new MigrationEvent(event, future));
    }
}
