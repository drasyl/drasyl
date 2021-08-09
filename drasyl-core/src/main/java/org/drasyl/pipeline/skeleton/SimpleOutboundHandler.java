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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of messages.
 * <p>
 * For example here is an implementation which only handle {@code MyMessage}s of type {@code
 * MyMessage}.
 *
 * <pre>
 *     public class ChunkedHandler extends
 *             {@link SimpleOutboundHandler}&lt;{@code MyMessage}, {@link IdentityPublicKey}&gt; {
 *
 *        {@code @Override}
 *         protected void matchedOutbound({@link HandlerContext} ctx,
 *             {@link IdentityPublicKey} recipient, {@code MyMessage} msg,
 *             {@link CompletableFuture}&lt;{@link Void}&gt; future) {
 *             System.out.println(msg);
 *         }
 *     }
 * </pre>
 */
@SuppressWarnings("java:S118")
public abstract class SimpleOutboundHandler<O, A extends Address> extends ChannelOutboundHandlerAdapter {
    private final TypeParameterMatcher matcherMessage;
    private final TypeParameterMatcher matcherAddress;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter
     * of the class.
     */
    protected SimpleOutboundHandler() {
        matcherMessage = TypeParameterMatcher.find(this, SimpleOutboundHandler.class, "O");
        matcherAddress = TypeParameterMatcher.find(this, SimpleOutboundHandler.class, "A");
    }

    @Skip
    @SuppressWarnings("java:S112")
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        if (matcherMessage.match(msg) && matcherAddress.match(recipient)) {
            @SuppressWarnings("unchecked") final O castedMsg = (O) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) recipient;
            matchedOutbound(ctx, castedAddress, castedMsg, future);
        }
        else {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new MigrationOutboundMessage<>(msg, recipient)))).combine(future);
        }
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
    protected abstract void matchedOutbound(ChannelHandlerContext ctx,
                                            A recipient,
                                            O msg,
                                            CompletableFuture<Void> future) throws Exception;

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof MigrationOutboundMessage) {
            final MigrationOutboundMessage<?, ?> migrationMsg = (MigrationOutboundMessage<?, ?>) msg;
            final CompletableFuture<Void> future = new CompletableFuture<>();
            FutureUtil.combine(future, promise);
            try {
                onOutbound(ctx, migrationMsg.address(), migrationMsg.message(), future);
            }
            catch (final Exception e) {
                future.completeExceptionally(e);
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }
}
