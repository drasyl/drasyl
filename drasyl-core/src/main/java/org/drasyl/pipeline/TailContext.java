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
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.scheduler.DrasylScheduler;

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

    @SuppressWarnings({ "java:S107" })
    public TailContext(final Consumer<Event> eventConsumer,
                       final DrasylConfig config,
                       final Pipeline pipeline,
                       final DrasylScheduler dependentScheduler,
                       final DrasylScheduler independentScheduler,
                       final Identity identity,
                       final PeersManager peersManager,
                       final Serialization inboundSerialization,
                       final Serialization outboundSerialization) {
        super(DRASYL_TAIL_HANDLER, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void onAdded(final HandlerContext ctx) {
        LOG.debug("Pipeline tail was added.");
    }

    @Override
    public void onRemoved(final HandlerContext ctx) {
        LOG.debug("Pipeline tail was removed.");
    }

    @Override
    public void onInbound(final HandlerContext ctx,
                          final Address sender,
                          final Object msg,
                          final CompletableFuture<Void> future) {
        if (msg instanceof AutoSwallow) {
            future.complete(null);
            return;
        }

        if (sender instanceof CompressedPublicKey) {
            final CompressedPublicKey senderAddress = (CompressedPublicKey) sender;
            final MessageEvent event = MessageEvent.of(senderAddress, msg);
            future.complete(null);
            LOG.trace("Event has passed the pipeline: `{}` ", event);

            eventConsumer.accept(event);
        }
        else {
            future.completeExceptionally(new IllegalStateException("Message was not written to the application, because the sender address was not of type `" + CompressedPublicKey.class.getSimpleName() + "` (was type `" + sender.getClass().getSimpleName() + "`)."));
            //noinspection unchecked
            LOG.debug("Message '{}' was not written to the application, because the sender address was not of type `{}` (was type `{}`).", () -> msg, CompressedPublicKey.class::getSimpleName, sender.getClass()::getSimpleName);
        }
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        future.complete(null);
        LOG.trace("Event has passed the pipeline: `{}` ", event);

        eventConsumer.accept(event);
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
