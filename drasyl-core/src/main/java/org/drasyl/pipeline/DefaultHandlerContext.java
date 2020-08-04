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
import org.drasyl.pipeline.codec.TypeValidator;

public class DefaultHandlerContext extends AbstractHandlerContext {
    private final Handler handler;

    public DefaultHandlerContext(String name,
                                 Handler handler,
                                 DrasylConfig config,
                                 Pipeline pipeline,
                                 Scheduler scheduler,
                                 Identity identity,
                                 TypeValidator validator) {
        super(name, config, pipeline, scheduler, identity, validator);
        this.handler = handler;
    }

    @Override
    public Handler handler() {
        return handler;
    }
}