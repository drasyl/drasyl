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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.codec.TypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings({ "java:S107", "java:S3077" })
abstract class AbstractHandlerContext implements HandlerContext {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHandlerContext.class);
    private final DrasylConfig config;
    private final Object prevLock = new Object();
    private final Object nextLock = new Object();
    private final String name;
    private final Pipeline pipeline;
    private final Scheduler scheduler;
    private final Identity identity;
    private final TypeValidator validator;
    private volatile AbstractHandlerContext prev;
    private volatile AbstractHandlerContext next;

    public AbstractHandlerContext(String name,
                                  DrasylConfig config,
                                  Pipeline pipeline,
                                  Scheduler scheduler,
                                  Identity identity,
                                  TypeValidator validator) {
        this(null, null, name, config, pipeline, scheduler, identity, validator);
    }

    AbstractHandlerContext(AbstractHandlerContext prev,
                           AbstractHandlerContext next,
                           String name,
                           DrasylConfig config,
                           Pipeline pipeline,
                           Scheduler scheduler,
                           Identity identity,
                           TypeValidator validator) {
        this.prev = prev;
        this.next = next;
        this.name = name;
        this.config = config;
        this.pipeline = pipeline;
        this.scheduler = scheduler;
        this.identity = identity;
        this.validator = validator;
    }

    void setPrevHandlerContext(AbstractHandlerContext prev) {
        synchronized (this.prevLock) {
            this.prev = prev;
        }
    }

    void setNextHandlerContext(AbstractHandlerContext next) {
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
    public String name() {
        return this.name;
    }

    @Override
    public HandlerContext fireExceptionCaught(Exception cause) {
        invokeExceptionCaught(cause);

        return this;
    }

    private void invokeExceptionCaught(Exception cause) {
        if (cause instanceof PipelineException) {
            throw (PipelineException) cause;
        }

        AbstractHandlerContext inboundCtx = findNextInbound();

        try {
            inboundCtx.handler().exceptionCaught(inboundCtx, cause);
        }
        catch (PipelineException e) {
            throw e;
        }
        catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke exceptionCaught() on next handler `{}` do to the following error: ", inboundCtx.name(), e);
            }
        }
    }

    /**
     * Finds the next {@link AbstractHandlerContext} for inbound messages and events.
     */
    AbstractHandlerContext findNextInbound() {
        AbstractHandlerContext nextInbound = next;
        while (nextInbound.handler() == null) {
            nextInbound = nextInbound.getNext();
        }

        return nextInbound;
    }

    /**
     * Finds the previous {@link AbstractHandlerContext} for outbound messages.
     */
    AbstractHandlerContext findPrevOutbound() {
        AbstractHandlerContext prevOutbound = prev;
        while (prevOutbound.handler() == null) {
            prevOutbound = prevOutbound.getPrev();
        }

        return prevOutbound;
    }

    @Override
    public CompletableFuture<Void> fireRead(CompressedPublicKey sender,
                                            Object msg,
                                            CompletableFuture<Void> future) {
        return invokeRead(sender, msg, future);
    }

    private CompletableFuture<Void> invokeRead(CompressedPublicKey sender,
                                               Object msg,
                                               CompletableFuture<Void> future) {
        AbstractHandlerContext inboundCtx = findNextInbound();
        try {
            inboundCtx.handler().read(inboundCtx, sender, msg, future);
        }
        catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke read() on next handler `{}` do to the following error: ", inboundCtx.name(), e);
            }
            future.completeExceptionally(e);
            inboundCtx.fireExceptionCaught(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> fireEventTriggered(Event event, CompletableFuture<Void> future) {
        return invokeEventTriggered(event, future);
    }

    private CompletableFuture<Void> invokeEventTriggered(Event event,
                                                         CompletableFuture<Void> future) {
        AbstractHandlerContext inboundCtx = findNextInbound();

        try {
            inboundCtx.handler().eventTriggered(inboundCtx, event, future);
        }
        catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke eventTriggered() on next handler `{}` do to the following error: ", inboundCtx.name(), e);
            }
            future.completeExceptionally(e);
            inboundCtx.fireExceptionCaught(e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> write(CompressedPublicKey recipient, Object msg) {
        return write(recipient, msg, new CompletableFuture<>());
    }

    @Override
    public CompletableFuture<Void> write(CompressedPublicKey recipient,
                                         Object msg,
                                         CompletableFuture<Void> future) {
        return invokeWrite(recipient, msg, future);
    }

    private CompletableFuture<Void> invokeWrite(CompressedPublicKey recipient,
                                                Object msg,
                                                CompletableFuture<Void> future) {
        AbstractHandlerContext outboundCtx = findPrevOutbound();

        try {
            outboundCtx.handler().write(outboundCtx, recipient, msg, future);
        }
        catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke write() on next handler `{}` do to the following error: ", outboundCtx.name(), e);
            }
            future.completeExceptionally(e);
            outboundCtx.fireExceptionCaught(e);
        }

        return future;
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
    public Scheduler scheduler() {
        return this.scheduler;
    }

    @Override
    public Identity identity() {
        return this.identity;
    }

    @Override
    public TypeValidator validator() {
        return this.validator;
    }
}
