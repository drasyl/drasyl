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
package org.drasyl.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.drasyl.util.scheduler.EmptyDisposable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import static org.drasyl.pipeline.HandlerMask.ON_EVENT_MASK;
import static org.drasyl.pipeline.HandlerMask.ON_EXCEPTION_MASK;
import static org.drasyl.pipeline.HandlerMask.ON_INBOUND_MASK;
import static org.drasyl.pipeline.HandlerMask.ON_OUTBOUND_MASK;

@SuppressWarnings({ "java:S107", "java:S3077" })
abstract class AbstractHandlerContext implements HandlerContext {
    private final DrasylConfig config;
    private final Object prevLock = new Object();
    private final Object nextLock = new Object();
    private Integer mask;
    private final String name;
    private final Pipeline pipeline;
    private final DrasylScheduler dependentScheduler;
    private final DrasylScheduler independentScheduler;
    private final Identity identity;
    private final PeersManager peersManager;
    private final Serialization inboundSerialization;
    private final Serialization outboundSerialization;
    private volatile AbstractHandlerContext prev;
    private volatile AbstractHandlerContext next;

    protected AbstractHandlerContext(final String name,
                                     final DrasylConfig config,
                                     final Pipeline pipeline,
                                     final DrasylScheduler dependentScheduler,
                                     final DrasylScheduler independentScheduler,
                                     final Identity identity,
                                     final PeersManager peersManager,
                                     final Serialization inboundSerialization,
                                     final Serialization outboundSerialization) {
        this(null, null, name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
    }

    AbstractHandlerContext(final AbstractHandlerContext prev,
                           final AbstractHandlerContext next,
                           final String name,
                           final DrasylConfig config,
                           final Pipeline pipeline,
                           final DrasylScheduler dependentScheduler,
                           final DrasylScheduler independentScheduler,
                           final Identity identity,
                           final PeersManager peersManager,
                           final Serialization inboundSerialization,
                           final Serialization outboundSerialization) {
        this.prev = prev;
        this.next = next;
        this.name = name;
        this.config = config;
        this.pipeline = pipeline;
        this.dependentScheduler = dependentScheduler;
        this.independentScheduler = independentScheduler;
        this.identity = identity;
        this.peersManager = peersManager;
        this.inboundSerialization = inboundSerialization;
        this.outboundSerialization = outboundSerialization;
    }

    void setPrevHandlerContext(final AbstractHandlerContext prev) {
        synchronized (this.prevLock) {
            this.prev = prev;
        }
    }

    void setNextHandlerContext(final AbstractHandlerContext next) {
        synchronized (this.nextLock) {
            this.next = next;
        }
    }

    AbstractHandlerContext getNext() {
        return this.next;
    }

    AbstractHandlerContext getPrev() {
        return this.prev;
    }

    @Override
    public ByteBuf alloc() {
        return alloc(true);
    }

    @Override
    public ByteBuf alloc(boolean preferDirect) {
        if (preferDirect) {
            return PooledByteBufAllocator.DEFAULT.ioBuffer();
        }
        else {
            return PooledByteBufAllocator.DEFAULT.heapBuffer();
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public HandlerContext passException(final Exception cause) {
        executeOnDependentScheduler(() -> invokeOnException(cause), () -> {
        });

        return this;
    }

    @SuppressWarnings("java:S2221")
    private void invokeOnException(final Exception cause) {
        final AbstractHandlerContext inboundCtx = findNextInbound(ON_EXCEPTION_MASK);
        try {
            if (inboundCtx != null) {
                inboundCtx.handler().onException(inboundCtx, cause);
            }
        }
        catch (final Exception e) {
            log().warn("Failed to invoke onException() on next handler `{}` do to the following error: ", inboundCtx::name, () -> e);
        }
    }

    /**
     * Finds the next {@link AbstractHandlerContext} for inbound messages and events that matches
     * the given {@code handlerMask}.
     *
     * @param handlerMask the handler mask that must be matched
     */
    AbstractHandlerContext findNextInbound(final int handlerMask) {
        AbstractHandlerContext nextInbound = next;
        while (nextInbound != null && (nextInbound.handler() == null
                || (nextInbound.getMask() & handlerMask) == 0)) {
            nextInbound = nextInbound.getNext();
        }

        return nextInbound;
    }

    /**
     * Finds the previous {@link AbstractHandlerContext} for outbound messages that matches the
     * given {@code handlerMask}.
     *
     * @param handlerMask the handler mask that must be matched
     */
    AbstractHandlerContext findPrevOutbound(final int handlerMask) {
        AbstractHandlerContext prevOutbound = prev;
        while (prevOutbound != null && (prevOutbound.handler() == null
                || (prevOutbound.getMask() & handlerMask) == 0)) {
            prevOutbound = prevOutbound.getPrev();
        }

        return prevOutbound;
    }

    @Override
    public CompletableFuture<Void> passInbound(final Address sender,
                                               final Object msg,
                                               final CompletableFuture<Void> future) {
        // check if future is done
        if (future.isDone()) {
            return future;
        }

        executeOnDependentScheduler(() -> invokeOnInbound(sender, msg, future), () -> {
            future.completeExceptionally(new RejectedExecutionException("Could not schedule task."));
            ReferenceCountUtil.safeRelease(msg);
        });

        return future;
    }

    @SuppressWarnings("java:S2221")
    private void invokeOnInbound(final Address sender,
                                 final Object msg,
                                 final CompletableFuture<Void> future) {
        final AbstractHandlerContext inboundCtx = findNextInbound(ON_INBOUND_MASK);
        try {
            if (inboundCtx != null) {
                inboundCtx.handler().onInbound(inboundCtx, sender, msg, future);
            }
        }
        catch (final Exception e) {
            future.completeExceptionally(e);
            inboundCtx.passException(e);
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public CompletableFuture<Void> passEvent(final Event event,
                                             final CompletableFuture<Void> future) {
        // check if future is done
        if (future.isDone()) {
            return future;
        }

        executeOnDependentScheduler(() -> invokeOnEvent(event, future), () -> {
        });

        return future;
    }

    @SuppressWarnings("java:S2221")
    private void invokeOnEvent(final Event event,
                               final CompletableFuture<Void> future) {
        final AbstractHandlerContext inboundCtx = findNextInbound(ON_EVENT_MASK);
        try {
            if (inboundCtx != null) {
                inboundCtx.handler().onEvent(inboundCtx, event, future);
            }
        }
        catch (final Exception e) {
            log().warn("Failed to invoke onEvent() on next handler `{}` do to the following error: ", inboundCtx::name, () -> e);
            future.completeExceptionally(e);
            inboundCtx.passException(e);
        }
    }

    @Override
    public CompletableFuture<Void> passOutbound(final Address recipient,
                                                final Object msg,
                                                final CompletableFuture<Void> future) {
        // check if future is done
        if (future.isDone()) {
            return future;
        }

        executeOnDependentScheduler(() -> invokeOnOutbound(recipient, msg, future), () -> {
            future.completeExceptionally(new RejectedExecutionException("Could not schedule task."));
            ReferenceCountUtil.safeRelease(msg);
        });

        return future;
    }

    @SuppressWarnings("java:S2221")
    private void invokeOnOutbound(final Address recipient,
                                  final Object msg,
                                  final CompletableFuture<Void> future) {
        final AbstractHandlerContext outboundCtx = findPrevOutbound(ON_OUTBOUND_MASK);
        try {
            if (outboundCtx != null) {
                outboundCtx.handler().onOutbound(outboundCtx, recipient, msg, future);
            }
        }
        catch (final Exception e) {
            future.completeExceptionally(e);
            outboundCtx.passException(e);
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    void executeOnDependentScheduler(final Runnable task,
                                     final Runnable releaseOnRejectedExecutionException) {
        if (this.dependentScheduler().isCalledFromThisScheduler()) {
            task.run();
        }
        else {
            if (this.dependentScheduler().scheduleDirect(task) == EmptyDisposable.INSTANCE) {
                releaseOnRejectedExecutionException.run();
            }
        }
    }

    @Override
    public DrasylConfig config() {
        return this.config;
    }

    @Override
    public Pipeline pipeline() {
        return this.pipeline;
    }

    @Override
    public DrasylScheduler dependentScheduler() {
        return this.dependentScheduler;
    }

    @Override
    public DrasylScheduler independentScheduler() {
        return this.independentScheduler;
    }

    @Override
    public Identity identity() {
        return this.identity;
    }

    @Override
    public PeersManager peersManager() {
        return this.peersManager;
    }

    @Override
    public Serialization inboundSerialization() {
        return this.inboundSerialization;
    }

    @Override
    public Serialization outboundSerialization() {
        return this.outboundSerialization;
    }

    Integer getMask() {
        // It is required to do this lazy at runtime to allow also anonymous classes to override the {@link Skip} annotation.
        if (mask == null) {
            mask = HandlerMask.mask(this.handler().getClass());
        }

        return mask;
    }

    protected abstract Logger log();
}
