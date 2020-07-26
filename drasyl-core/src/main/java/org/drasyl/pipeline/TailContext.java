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
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Special class that represents the tail of a {@link Pipeline}. This class can not be removed from
 * the pipeline.
 */
class TailContext extends AbstractHandlerContext implements Handler {
    public static final String DRASYL_TAIL_HANDLER = "DRASYL_TAIL_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(TailContext.class);
    private final Consumer<Event> eventConsumer;

    public TailContext(Consumer<Event> eventConsumer,
                       DrasylConfig config,
                       Pipeline pipeline,
                       Scheduler scheduler,
                       Identity identity,
                       TypeValidator validator) {
        super(DRASYL_TAIL_HANDLER, config, pipeline, scheduler, identity, validator);
        this.eventConsumer = eventConsumer;
    }

    @Override
    public Handler handler() {
        return this;
    }

    @Override
    public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
        // Pass message to Application
        eventConsumer.accept(new MessageEvent(Pair.of(sender, msg)));
    }

    @Override
    public void eventTriggered(HandlerContext ctx, Event event) {
        // Pass event to Application
        eventConsumer.accept(event);
    }

    @Override
    public void exceptionCaught(HandlerContext ctx, Exception cause) {
        throw new PipelineException(cause);
    }

    @Override
    public void write(HandlerContext ctx,
                      CompressedPublicKey recipient,
                      Object msg,
                      CompletableFuture<Void> future) {
        ctx.write(recipient, msg, future);
    }

    @Override
    public void handlerAdded(HandlerContext ctx) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Pipeline tail was added.");
        }
    }

    @Override
    public void handlerRemoved(HandlerContext ctx) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Pipeline tail was removed.");
        }
    }
}
