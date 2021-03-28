/*
 * Copyright (c) 2020-2021.
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
