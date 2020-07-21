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
import org.drasyl.DrasylException;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Special class that represents the head of a {@link Pipeline}. This class can not be removed from
 * the pipeline.
 */
class HeadContext extends AbstractHandlerContext implements InboundHandler, OutboundHandler {
    public static final String DRASYL_HEAD_HANDLER = "DRASYL_HEAD_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(HeadContext.class);
    private final DrasylConsumer<ApplicationMessage, DrasylException> outboundConsumer;

    public HeadContext(DrasylConsumer<ApplicationMessage, DrasylException> outboundConsumer,
                       DrasylConfig config,
                       Pipeline pipeline,
                       Scheduler scheduler,
                       Supplier<Identity> identity,
                       TypeValidator validator) {
        super(DRASYL_HEAD_HANDLER, config, pipeline, scheduler, identity, validator);
        this.outboundConsumer = outboundConsumer;
    }

    @Override
    public Handler handler() {
        return this;
    }

    @Override
    public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
        ctx.fireRead(sender, msg);
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
                      CompressedPublicKey recipient,
                      Object msg,
                      CompletableFuture<Void> future) {
        if (future.isDone()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Message `{}` was not written to the underlying drasyl layer, because the corresponding future was already completed.", msg);
            }
        }
        else {
            try {
                // Here we want to combine the input into an ApplicationMessage,
                // therefore the msg must be a ObjectHolder at this point
                if (msg instanceof ObjectHolder) {
                    ObjectHolder oh = (ObjectHolder) msg;
                    outboundConsumer.accept(new ApplicationMessage(identity().getPublicKey(), recipient, oh.getObject(), oh.getClazz()));
                    future.complete(null);
                }
                else {
                    future.completeExceptionally(new IllegalArgumentException("Message must be a ObjectHolder at the end of the pipeline."));
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Message `{}` was not written to the underlying drasyl layer, because the message was not of type ObjectHolder at the end of the pipeline.", msg);
                    }
                }
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
