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
class TailContext extends AbstractEndHandler {
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

    @Override
    public void read(HandlerContext ctx,
                     CompressedPublicKey sender,
                     Object msg,
                     CompletableFuture<Void> future) {
        // Pass message to Application
        if (future.isDone()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Message `{}` was not written to the application, because the corresponding future was already completed.", msg);
            }
        }
        else {
            eventConsumer.accept(new MessageEvent(Pair.of(sender, msg)));
            future.complete(null);
        }
    }

    @Override
    public void eventTriggered(HandlerContext ctx, Event event, CompletableFuture<Void> future) {
        // Pass event to Application
        if (future.isDone()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Event `{}` was not written to the application, because the corresponding future was already completed.", event);
            }
        }
        else {
            eventConsumer.accept(event);
            future.complete(null);
        }
    }

    @Override
    public void exceptionCaught(HandlerContext ctx, Exception cause) {
        throw new PipelineException(cause);
    }
}