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
import org.drasyl.util.CheckedConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

final class HeadContext extends AbstractHandlerContext implements InboundHandler, OutboundHandler {
    public static final String DRASYL_HEAD_HANDLER = "DRASYL_HEAD_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(HeadContext.class);
    private final CheckedConsumer<ApplicationMessage> outboundConsumer;

    public HeadContext(CheckedConsumer<ApplicationMessage> outboundConsumer) {
        super(DRASYL_HEAD_HANDLER);
        this.outboundConsumer = outboundConsumer;
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
    public void exceptionCaught(HandlerContext ctx, Exception cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void write(HandlerContext ctx,
                      ApplicationMessage msg,
                      CompletableFuture<Void> future) {
        if (future.isDone()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Message `{}` was not written to the underlying drasyl layer, because the corresponding future was already completed.", msg);
            }
        }
        else {
            try {
                outboundConsumer.accept(msg);
                future.complete(null);
            }
            catch (Exception e) {
                future.completeExceptionally(e);
            }
        }
    }

    @Override
    public void handlerAdded(HandlerContext ctx) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Pipeline head was added.");
        }
    }

    @Override
    public void handlerRemoved(HandlerContext ctx) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Pipeline head was removed.");
        }
    }
}
