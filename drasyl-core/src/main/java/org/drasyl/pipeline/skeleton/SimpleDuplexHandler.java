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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.MigrationOutboundMessage;
import org.drasyl.pipeline.Skip;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;

import java.util.concurrent.CompletableFuture;

/**
 * {@link HandlerAdapter} which allows to explicit only handle a specific type of messages and
 * events.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks", "java:S118" })
public abstract class SimpleDuplexHandler<I, O, A extends Address> extends ChannelDuplexHandler {
    private final TypeParameterMatcher outboundMessageMatcher;
    private final TypeParameterMatcher matcherMessage;
    private final TypeParameterMatcher matcherAddress;

    protected SimpleDuplexHandler() {
        this.matcherMessage = TypeParameterMatcher.find(this, SimpleDuplexHandler.class, "I");
        this.matcherAddress = TypeParameterMatcher.find(this, SimpleDuplexHandler.class, "A");
        this.outboundMessageMatcher = TypeParameterMatcher.find(this, SimpleDuplexHandler.class, "O");
    }

    protected SimpleDuplexHandler(final Class<? extends I> inboundMessageType,
                                  final Class<? extends O> outboundMessageType,
                                  final Class<? extends A> addressType) {
        this.matcherMessage = TypeParameterMatcher.get(inboundMessageType);
        this.matcherAddress = TypeParameterMatcher.get(addressType);
        this.outboundMessageMatcher = TypeParameterMatcher.get(outboundMessageType);
    }

    @Skip
    @SuppressWarnings("java:S112")
    public void onOutbound(final ChannelHandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) throws Exception {
        if (outboundMessageMatcher.match(msg) && matcherAddress.match(recipient)) {
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

    @Skip
    @SuppressWarnings("java:S112")
    public void onInbound(final ChannelHandlerContext ctx,
                          final Address sender,
                          final Object msg) throws Exception {
        if (matcherMessage.match(msg) && matcherAddress.match(sender)) {
            @SuppressWarnings("unchecked") final I castedMsg = (I) msg;
            @SuppressWarnings("unchecked") final A castedAddress = (A) sender;
            matchedInbound(ctx, castedAddress, castedMsg);
        }
        else {
            ctx.fireChannelRead(new AddressedMessage<>(msg, sender));
        }
    }

    /**
     * Is called for each message of type {@link I}.
     *
     * @param ctx    handler context
     * @param sender the sender of the message
     * @param msg    the message
     */
    @SuppressWarnings("java:S112")
    protected abstract void matchedInbound(ChannelHandlerContext ctx,
                                           A sender,
                                           I msg) throws Exception;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof AddressedMessage) {
            final AddressedMessage<?, ? extends Address> migrationMsg = (AddressedMessage<?, ? extends Address>) msg;
            try {
                onInbound(ctx, migrationMsg.address(), migrationMsg.message());
            }
            catch (final Exception e) {
                ctx.fireExceptionCaught(e);
                ReferenceCountUtil.safeRelease(migrationMsg.message());
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }
}
