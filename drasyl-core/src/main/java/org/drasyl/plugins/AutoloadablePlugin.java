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
package org.drasyl.plugins;

import org.drasyl.DrasylConfig;
import org.drasyl.pipeline.Pipeline;

/**
 * This class must be extended by all {@link DrasylPlugin}s that should be auto-loaded by drasyl.
 */
public abstract class AutoloadablePlugin implements DrasylPlugin {
    protected final Pipeline pipeline;
    protected final DrasylConfig config;
    protected final PluginEnvironment environment;

    public AutoloadablePlugin(Pipeline pipeline,
                              DrasylConfig config,
                              PluginEnvironment environment) {
        this.pipeline = pipeline;
        this.config = config;
        this.environment = environment;
    }
}
