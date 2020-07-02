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
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractHandlerContext implements HandlerContext {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHandlerContext.class);
    private final Object prevLock = new Object();
    private final Object nextLock = new Object();
    volatile AbstractHandlerContext prev; // NOSONAR
    volatile AbstractHandlerContext next; // NOSONAR
    private final String name;

    public AbstractHandlerContext(String name) {
        this.name = name;
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

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public HandlerContext fireExceptionCaught(Throwable cause) {
        invokeExceptionCaught(cause);

        return this;
    }

    private void invokeExceptionCaught(Throwable cause) {
        AbstractHandlerContext inboundCtx = findNextInbound();

        if (inboundCtx.handler() instanceof InboundHandler) {
            try {
                ((InboundHandler) inboundCtx.handler()).exceptionCaught(inboundCtx, cause);
            }
            catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to invoke exceptionCaught() on next handler `{}` do to the following error: ", inboundCtx.name(), e);
                }
            }
        }
    }

    /**
     * Finds the next {@link AbstractHandlerContext} that holds a {@link InboundHandler}.
     */
    private AbstractHandlerContext findNextInbound() {
        AbstractHandlerContext nextInbound = next;
        while (!(next.handler() instanceof InboundHandler)) {
            nextInbound = next.next;
        }

        return nextInbound;
    }

    /**
     * Finds the previous {@link AbstractHandlerContext} that holds a {@link OutboundHandler}.
     */
    private AbstractHandlerContext findPrevOutbound() {
        AbstractHandlerContext prevOutbound = prev;
        while (!(prev.handler() instanceof OutboundHandler)) {
            prevOutbound = prev.prev;
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

        if (inboundCtx.handler() instanceof InboundHandler) {
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
    }

    @Override
    public HandlerContext fireEventTriggered(Event event) {
        invokeEventTriggered(event);

        return this;
    }

    private void invokeEventTriggered(Event event) {
        AbstractHandlerContext inboundCtx = findNextInbound();

        if (inboundCtx.handler() instanceof InboundHandler) {
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

        if (outboundCtx.handler() instanceof OutboundHandler) {
            try {
                ((OutboundHandler) outboundCtx.handler()).write(outboundCtx, msg, future);
            }
            catch (Exception e) {
                outboundCtx.fireExceptionCaught(e);
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Failed to invoke write() on next handler `{}` do to the following error: ", outboundCtx.name(), e);
                }
            }
        }

        return future;
    }
}
