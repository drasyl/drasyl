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
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.codec.TypeValidator;

/**
 * The default handler context implementation.
 */
@SuppressWarnings({ "java:S107" })
public class DefaultHandlerContext extends AbstractHandlerContext {
    private final Handler handler;

    /**
     * Generates a new default handler context for the given {@code handler}.
     *
     * @param name              the name of the handler
     * @param handler           the handler
     * @param config            the config of the drasyl node
     * @param pipeline          the corresponding pipeline object
     * @param scheduler         the corresponding scheduler
     * @param identity          the identity of the corresponding node
     * @param peersManager      the peers manager of the corresponding node
     * @param inboundValidator  the inbound validator of the pipeline
     * @param outboundValidator the outbound validator of the pipeline
     */
    public DefaultHandlerContext(final String name,
                                 final Handler handler,
                                 final DrasylConfig config,
                                 final Pipeline pipeline,
                                 final Scheduler scheduler,
                                 final Identity identity,
                                 final PeersManager peersManager,
                                 final TypeValidator inboundValidator,
                                 final TypeValidator outboundValidator) {
        super(name, config, pipeline, scheduler, identity, peersManager, inboundValidator, outboundValidator);
        this.handler = handler;
    }

    @Override
    public Handler handler() {
        return handler;
    }
}