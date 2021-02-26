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
package org.drasyl.plugin;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.Pipeline;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Models environment information of a {@link DrasylPlugin} that are required by the plugin.
 */
public class PluginEnvironment {
    private final DrasylConfig config;
    private final Identity identity;
    private final Pipeline pipeline;

    public PluginEnvironment(final DrasylConfig config,
                             final Identity identity,
                             final Pipeline pipeline) {
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        this.pipeline = requireNonNull(pipeline);
    }

    public DrasylConfig getConfig() {
        return config;
    }

    public Identity getIdentity() {
        return identity;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PluginEnvironment that = (PluginEnvironment) o;
        return Objects.equals(config, that.config) &&
                Objects.equals(pipeline, that.pipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, pipeline);
    }
}
