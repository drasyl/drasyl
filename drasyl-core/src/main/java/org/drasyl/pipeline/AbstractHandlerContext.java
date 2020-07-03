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

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractHandlerContext implements HandlerContext {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHandlerContext.class);
    private final DrasylConfig config;
    private final Object prevLock = new Object();
    private final Object nextLock = new Object();
    private final String name;
    private volatile AbstractHandlerContext prev; // NOSONAR
    private volatile AbstractHandlerContext next; // NOSONAR

    AbstractHandlerContext(AbstractHandlerContext prev,
                           AbstractHandlerContext next,
                           String name,
                           DrasylConfig config) {
        this.prev = prev;
        this.next = next;
        this.name = name;
        this.config = config;
    }

    public AbstractHandlerContext(String name, DrasylConfig config) {
        this.name = name;
        this.config = config;
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
        AbstractHandlerContext inboundCtx = findNextInbound();

        if (cause instanceof PipelineException) {
            throw (PipelineException) cause;
        }

        try {
            ((InboundHandler) inboundCtx.handler()).exceptionCaught(inboundCtx, cause);
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
     * Finds the next {@link AbstractHandlerContext} that holds a {@link InboundHandler}.
     */
    AbstractHandlerContext findNextInbound() {
        AbstractHandlerContext nextInbound = next;
        while (!(nextInbound.handler() instanceof InboundHandler)) {
            nextInbound = next.getNext();
        }

        return nextInbound;
    }

    /**
     * Finds the previous {@link AbstractHandlerContext} that holds a {@link OutboundHandler}.
     */
    AbstractHandlerContext findPrevOutbound() {
        AbstractHandlerContext prevOutbound = prev;
        while (!(prevOutbound.handler() instanceof OutboundHandler)) {
            prevOutbound = prev.getPrev();
        }

        return prevOutbound;
    }

    @Override
    public HandlerContext fireRead(ApplicationMessage msg) {
        invokeRead(msg);

        return this;
    }

    private void invokeRead(ApplicationMessage msg) {
        AbstractHandlerContext inboundCtx = findNextInbound();
        try {
            ((InboundHandler) inboundCtx.handler()).read(inboundCtx, msg);
        }
        catch (Exception e) {
            inboundCtx.fireExceptionCaught(e);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke read() on next handler `{}` do to the following error: ", inboundCtx.name(), e);
            }
        }
    }

    @Override
    public HandlerContext fireEventTriggered(Event event) {
        invokeEventTriggered(event);

        return this;
    }

    private void invokeEventTriggered(Event event) {
        AbstractHandlerContext inboundCtx = findNextInbound();

        try {
            ((InboundHandler) inboundCtx.handler()).eventTriggered(inboundCtx, event);
        }
        catch (Exception e) {
            inboundCtx.fireExceptionCaught(e);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke eventTriggered() on next handler `{}` do to the following error: ", inboundCtx.name(), e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> write(ApplicationMessage msg) {
        return write(msg, new CompletableFuture<>());
    }

    @Override
    public CompletableFuture<Void> write(ApplicationMessage msg,
                                         CompletableFuture<Void> future) {
        return invokeWrite(msg, future);
    }

    private CompletableFuture<Void> invokeWrite(ApplicationMessage msg,
                                                CompletableFuture<Void> future) {
        AbstractHandlerContext outboundCtx = findPrevOutbound();

        try {
            ((OutboundHandler) outboundCtx.handler()).write(outboundCtx, msg, future);
        }
        catch (Exception e) {
            outboundCtx.fireExceptionCaught(e);
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to invoke write() on next handler `{}` do to the following error: ", outboundCtx.name(), e);
            }
        }

        return future;
    }

    @Override
    public DrasylConfig config() {
        return this.config;
    }
}
