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
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.scheduler.DrasylScheduler;

import java.util.concurrent.CompletableFuture;

/**
 * Special class that represents the head of a {@link Pipeline}. This class can not be removed from
 * the pipeline.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks", "java:S107" })
class HeadContext extends AbstractEndHandler {
    public static final String DRASYL_HEAD_HANDLER = "DRASYL_HEAD_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(HeadContext.class);

    public HeadContext(final DrasylConfig config,
                       final Pipeline pipeline,
                       final DrasylScheduler dependentScheduler,
                       final DrasylScheduler independentScheduler,
                       final Identity identity,
                       final PeersManager peersManager,
                       final Serialization inboundSerialization,
                       final Serialization outboundSerialization) {
        super(DRASYL_HEAD_HANDLER, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
    }

    @Override
    public void onAdded(final HandlerContext ctx) {
        LOG.debug("Pipeline head was added.");
    }

    @Override
    public void onRemoved(final HandlerContext ctx) {
        LOG.debug("Pipeline head was removed.");
    }

    @Override
    public void onOutbound(final HandlerContext ctx,
                           final Address recipient,
                           final Object msg,
                           final CompletableFuture<Void> future) {
        try {
            if (msg instanceof AutoSwallow) {
                future.complete(null);
                return;
            }

            LOG.warn("Message `{}` with recipient `{}` has arrived at the end of the pipeline and was not consumed before by a handler. Therefore the message was dropped.\n" +
                    "This can happen if none of the handlers in the pipeline can process this message or have no route to the recipient.", msg, recipient);
            future.completeExceptionally(new IllegalStateException("Message has arrived at the end of the pipeline and was not consumed before by a handler. Therefore the message was dropped. This can happen if none of the handlers in the pipeline can process this message or have no route to the recipient."));
        }
        finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
