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

import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.CheckedConsumer;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The default {@link Pipeline} implementation. Used to implement plugins for drasyl.
 */
public class DrasylPipeline implements Pipeline {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylPipeline.class);
    private final Map<String, AbstractHandlerContext> handlerNames;
    private final AbstractHandlerContext head;
    private final AbstractHandlerContext tail;
    private final Consumer<Event> eventConsumer;
    private final CheckedConsumer<ApplicationMessage> outboundConsumer;

    public DrasylPipeline(Consumer<Event> eventConsumer,
                          CheckedConsumer<ApplicationMessage> outboundConsumer) {
        this.eventConsumer = eventConsumer;
        this.outboundConsumer = outboundConsumer;
        this.handlerNames = new ConcurrentHashMap<>();
        this.head = new HeadContext();
        this.tail = new TailContext();
        this.head.setPrevHandlerContext(this.head);
        this.head.setNextHandlerContext(this.tail);
        this.tail.setPrevHandlerContext(this.head);
    }

    @Override
    public Pipeline addFirst(String name, Handler handler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.head);
            newCtx.setNextHandlerContext(this.head.next);

            // Set correct pointer on old context
            this.head.next.setPrevHandlerContext(newCtx);
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
    private void collisionCheck(String name) {
        if (handlerNames.containsKey(name)) {
            throw new IllegalArgumentException("A handler with this name is already registered");
        }
    }

    private void registerNewHandler(String name, AbstractHandlerContext handlerCtx) {
        // Add to handlerName list
        handlerNames.put(name, handlerCtx);

        // Call handler added
        try {
            handlerCtx.handler().handlerAdded(handlerCtx);
        }
        catch (Exception e) {
            LOG.warn("Error on adding handler `{}`: ", handlerCtx.name(), e);
        }
    }

    @Override
    public Pipeline addLast(String name, Handler handler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.tail.prev);
            newCtx.setNextHandlerContext(this.tail);

            // Set correct pointer on old context
            this.tail.prev.setNextHandlerContext(newCtx);
            this.tail.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addBefore(String baseName, String name, Handler handler) {
        Objects.requireNonNull(baseName);
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            Objects.requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx.prev);
            newCtx.setNextHandlerContext(baseCtx);

            // Set correct pointer on old context
            baseCtx.setPrevHandlerContext(newCtx);
            baseCtx.prev.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addAfter(String baseName, String name, Handler handler) {
        Objects.requireNonNull(baseName);
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            Objects.requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx);
            newCtx.setNextHandlerContext(baseCtx.next);

            // Set correct pointer on old context
            baseCtx.next.setPrevHandlerContext(newCtx);
            baseCtx.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline remove(String name) {
        Objects.requireNonNull(name);

        synchronized (this) {
            AbstractHandlerContext ctx = handlerNames.remove(name);
            if (ctx == null) {
                throw new NoSuchElementException("There is no handler with this name in the pipeline");
            }

            AbstractHandlerContext prev = ctx.prev;
            AbstractHandlerContext next = ctx.next;
            prev.setNextHandlerContext(next);
            next.setPrevHandlerContext(prev);
            ctx.setPrevHandlerContext(null);
            ctx.setNextHandlerContext(null);
        }

        return this;
    }

    @Override
    public Pipeline replace(String oldName, String newName, Handler newHandler) {
        Objects.requireNonNull(oldName);
        Objects.requireNonNull(newName);
        Objects.requireNonNull(newHandler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            if (!oldName.equals(newName)) {
                collisionCheck(newName);
            }

            AbstractHandlerContext oldCtx = handlerNames.remove(oldName);
            AbstractHandlerContext prev = oldCtx.prev;
            AbstractHandlerContext next = oldCtx.next;

            newCtx = new DefaultHandlerContext(newName, newHandler);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(prev);
            newCtx.setNextHandlerContext(next);

            // Delete old pointers
            oldCtx.setPrevHandlerContext(null);
            oldCtx.setNextHandlerContext(null);

            // Set correct pointer on old context
            prev.setNextHandlerContext(newCtx);
            next.setPrevHandlerContext(newCtx);

            registerNewHandler(newName, newCtx);
        }

        return this;
    }

    @Override
    public Handler get(String name) {
        Objects.requireNonNull(name);

        if (handlerNames.containsKey(name)) {
            return handlerNames.get(name).handler();
        }

        return null;
    }

    @Override
    public HandlerContext context(String name) {
        Objects.requireNonNull(name);

        return handlerNames.get(name);
    }

    @Override
    public void executeInbound(ApplicationMessage msg) {
        this.head.fireRead(msg);
    }

    @Override
    public void executeInbound(Event event) {
        this.head.fireEventTriggered(event);
    }

    @Override
    public void executeOutbound(ApplicationMessage msg) {
        this.tail.write(msg);
    }

    final class HeadContext extends AbstractHandlerContext implements InboundHandler, OutboundHandler {
        public HeadContext() {
            super("DRASYL_HEAD_HANDLER");
        }

        @Override
        public Handler handler() {
            return this;
        }

        @Override
        public void read(HandlerContext ctx, ApplicationMessage msg) {
            ctx.fireRead(msg);
        }

        @Override
        public void eventTriggered(HandlerContext ctx, Event event) {
            ctx.fireEventTriggered(event);
        }

        @Override
        public void exceptionCaught(HandlerContext ctx, Throwable cause) throws Exception {
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public void write(HandlerContext ctx,
                          ApplicationMessage msg,
                          CompletableFuture<Void> future) {
            if (!future.isCancelled() && !future.isCompletedExceptionally()) {
                try {
                    outboundConsumer.accept(msg);
                    future.complete(null);
                }
                catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
            else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Message `{}` was not written to the underlying drasyl layer, because the corresponding was not completed successfully", msg);
                }
            }
        }

        @Override
        public void handlerAdded(HandlerContext ctx) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Pipeline head was added.");
            }
        }

        @Override
        public void handlerRemoved(HandlerContext ctx) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Pipeline head was removed.");
            }
        }
    }

    final class TailContext extends AbstractHandlerContext implements InboundHandler, OutboundHandler {
        public TailContext() {
            super("DRASYL_TAIL_HANDLER");
        }

        @Override
        public Handler handler() {
            return this;
        }

        @Override
        public void read(HandlerContext ctx, ApplicationMessage msg) {
            // Pass message to Application
            eventConsumer.accept(new MessageEvent(Pair.of(msg.getSender(), msg.getPayload())));
        }

        @Override
        public void eventTriggered(HandlerContext ctx, Event event) {
            // Pass event to Application
            eventConsumer.accept(event);
        }

        @Override
        public void exceptionCaught(HandlerContext ctx, Throwable cause) throws Exception {
            throw new PipelineException(cause);
        }

        @Override
        public void write(HandlerContext ctx,
                          ApplicationMessage msg,
                          CompletableFuture<Void> future) {
            ctx.write(msg, future);
        }

        @Override
        public void handlerAdded(HandlerContext ctx) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Pipeline tail was added.");
            }
        }

        @Override
        public void handlerRemoved(HandlerContext ctx) throws Exception {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Pipeline tail was removed.");
            }
        }
    }
}
