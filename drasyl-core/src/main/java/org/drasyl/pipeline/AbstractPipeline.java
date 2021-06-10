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

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.scheduler.DrasylScheduler;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Abstract {@link Pipeline} implementation, that needs head and tail.
 */
public abstract class AbstractPipeline implements Pipeline {
    protected final Map<String, AbstractHandlerContext> handlerNames;
    protected AbstractEndHandler head;
    protected AbstractEndHandler tail;
    protected final DrasylScheduler dependentScheduler;
    protected final DrasylScheduler independentScheduler;
    protected final DrasylConfig config;
    protected final Identity identity;
    protected final PeersManager peersManager;
    protected final Serialization inboundSerialization;
    protected final Serialization outboundSerialization;
    protected final Semaphore outboundMessagesBuffer;

    @SuppressWarnings("java:S107")
    protected AbstractPipeline(final Map<String, AbstractHandlerContext> handlerNames,
                               final DrasylScheduler dependentScheduler,
                               final DrasylScheduler independentScheduler,
                               final DrasylConfig config,
                               final Identity identity,
                               final PeersManager peersManager,
                               final Serialization inboundSerialization,
                               final Serialization outboundSerialization,
                               final Semaphore outboundMessagesBuffer) {
        this.handlerNames = requireNonNull(handlerNames);
        this.dependentScheduler = requireNonNull(dependentScheduler);
        this.independentScheduler = requireNonNull(independentScheduler);
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        this.peersManager = requireNonNull(peersManager);
        this.inboundSerialization = requireNonNull(inboundSerialization);
        this.outboundSerialization = requireNonNull(outboundSerialization);
        this.outboundMessagesBuffer = outboundMessagesBuffer;
    }

    @SuppressWarnings("java:S2221")
    protected void initPointer() {
        this.head.setNextHandlerContext(this.tail);
        this.tail.setPrevHandlerContext(this.head);
        try {
            this.head.handler().onAdded(this.head);
            this.tail.handler().onAdded(this.head);
        }
        catch (final Exception e) {
            this.head.passException(e);
        }
    }

    @Override
    public Pipeline addFirst(final String name, final Handler handler) {
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.head);
            newCtx.setNextHandlerContext(this.head.getNext());

            // Set correct pointer on old context
            this.head.getNext().setPrevHandlerContext(newCtx);
            this.head.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    /**
     * Checks if the handler is already registered, if so an {@link IllegalArgumentException} is
     * thrown.
     *
     * @param name handler name
     * @throws IllegalArgumentException if handler is already registered
     */
    private void collisionCheck(final String name) {
        if (handlerNames.containsKey(name)) {
            throw new IllegalArgumentException("A handler with this name is already registered");
        }
    }

    @SuppressWarnings("java:S2221")
    private void registerNewHandler(final String name, final AbstractHandlerContext handlerCtx) {
        // Add to handlerName list
        handlerNames.put(name, handlerCtx);

        // Call handler added
        try {
            handlerCtx.handler().onAdded(handlerCtx);
        }
        catch (final Exception e) {
            handlerCtx.passException(e);
            log().warn("Error on adding handler `{}`: ", handlerCtx::name, () -> e);
        }
    }

    @Override
    public Pipeline addLast(final String name, final Handler handler) {
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.tail.getPrev());
            newCtx.setNextHandlerContext(this.tail);

            // Set correct pointer on old context
            this.tail.getPrev().setNextHandlerContext(newCtx);
            this.tail.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addBefore(final String baseName, final String name, final Handler handler) {
        requireNonNull(baseName);
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            final AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx.getPrev());
            newCtx.setNextHandlerContext(baseCtx);

            // Set correct pointer on old context
            baseCtx.getPrev().setNextHandlerContext(newCtx);
            baseCtx.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addAfter(final String baseName, final String name, final Handler handler) {
        requireNonNull(baseName);
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            final AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx);
            newCtx.setNextHandlerContext(baseCtx.getNext());

            // Set correct pointer on old context
            baseCtx.getNext().setPrevHandlerContext(newCtx);
            baseCtx.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline remove(final String name) {
        requireNonNull(name);

        synchronized (this) {
            final AbstractHandlerContext ctx = handlerNames.remove(name);
            if (ctx == null) {
                throw new NoSuchElementException("There is no handler with this name in the pipeline");
            }

            // call remove action
            removeHandlerAction(ctx);

            final AbstractHandlerContext prev = ctx.getPrev();
            final AbstractHandlerContext next = ctx.getNext();
            prev.setNextHandlerContext(next);
            next.setPrevHandlerContext(prev);
        }

        return this;
    }

    @SuppressWarnings("java:S2221")
    private void removeHandlerAction(final AbstractHandlerContext ctx) {
        // call remove action
        try {
            ctx.handler().onRemoved(ctx);
        }
        catch (final Exception e) {
            ctx.passException(e);
            log().warn("Error on adding handler `{}`: ", ctx::name, () -> e);
        }
    }

    @Override
    public Pipeline replace(final String oldName, final String newName, final Handler newHandler) {
        requireNonNull(oldName);
        requireNonNull(newName);
        requireNonNull(newHandler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            if (!oldName.equals(newName)) {
                collisionCheck(newName);
            }

            final AbstractHandlerContext oldCtx = handlerNames.remove(oldName);
            final AbstractHandlerContext prev = oldCtx.getPrev();
            final AbstractHandlerContext next = oldCtx.getNext();

            // call remove action
            removeHandlerAction(oldCtx);

            newCtx = new DefaultHandlerContext(newName, newHandler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(prev);
            newCtx.setNextHandlerContext(next);

            // Set correct pointer on old context
            prev.setNextHandlerContext(newCtx);
            next.setPrevHandlerContext(newCtx);

            registerNewHandler(newName, newCtx);
        }

        return this;
    }

    @Override
    public Handler get(final String name) {
        requireNonNull(name);

        if (handlerNames.containsKey(name)) {
            return handlerNames.get(name).handler();
        }

        return null;
    }

    @Override
    public HandlerContext context(final String name) {
        requireNonNull(name);

        return handlerNames.get(name);
    }

    @Override
    public CompletableFuture<Void> processInbound(final Address sender,
                                                  final Object msg) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.head.passInbound(sender, msg, rtn);

        return rtn;
    }

    @Override
    public CompletableFuture<Void> processInbound(final Event event) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.head.passEvent(event, rtn);

        return rtn;
    }

    @Override
    public CompletableFuture<Void> processOutbound(final Address recipient,
                                                   final Object msg) {
        if (outboundMessagesBuffer == null) {
            final CompletableFuture<Void> rtn = new CompletableFuture<>();
            this.tail.passOutbound(recipient, msg, rtn);
            return rtn;
        }
        else if (outboundMessagesBuffer.tryAcquire()) {
            final CompletableFuture<Void> rtn = new CompletableFuture<>();
            rtn.whenComplete((unused, e) -> outboundMessagesBuffer.release());
            this.tail.passOutbound(recipient, msg, rtn);
            return rtn;
        }
        else {
            return failedFuture(new Exception("Outbound messages buffer capacity exceeded. New messages can only be enqueued once the previous ones have been processed. Alternatively drasyl.message.buffer-size can be increased."));
        }
    }

    @Override
    public boolean isWritable() {
        return outboundMessagesBuffer == null || outboundMessagesBuffer.availablePermits() > 0;
    }

    @Override
    public int messagesBeforeUnwritable() {
        if (outboundMessagesBuffer != null) {
            return outboundMessagesBuffer.availablePermits();
        }
        else {
            return Integer.MAX_VALUE;
        }
    }

    protected abstract Logger log();
}
