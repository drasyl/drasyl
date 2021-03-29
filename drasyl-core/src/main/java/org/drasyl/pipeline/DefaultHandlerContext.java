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
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.scheduler.DrasylScheduler;

/**
 * The default handler context implementation.
 */
@SuppressWarnings({ "java:S107" })
public class DefaultHandlerContext extends AbstractHandlerContext {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHandlerContext.class);
    private final Handler handler;

    /**
     * Generates a new default handler context for the given {@code handler}.
     *
     * @param name                  the name of the handler
     * @param handler               the handler
     * @param config                the config of the drasyl node
     * @param pipeline              the corresponding pipeline object
     * @param dependentScheduler    the dependent scheduler
     * @param independentScheduler  the independent scheduler
     * @param identity              the identity of the corresponding node
     * @param peersManager          the peers manager of the corresponding node
     * @param inboundSerialization  the inbound serialization of the pipeline
     * @param outboundSerialization the outbound serialization of the pipeline
     */
    public DefaultHandlerContext(final String name,
                                 final Handler handler,
                                 final DrasylConfig config,
                                 final Pipeline pipeline,
                                 final DrasylScheduler dependentScheduler,
                                 final DrasylScheduler independentScheduler,
                                 final Identity identity,
                                 final PeersManager peersManager,
                                 final Serialization inboundSerialization,
                                 final Serialization outboundSerialization) {
        super(name, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
        this.handler = handler;
    }

    @Override
    public Handler handler() {
        return handler;
    }

    @Override
    protected Logger log() {
        return LOG;
    }
}
