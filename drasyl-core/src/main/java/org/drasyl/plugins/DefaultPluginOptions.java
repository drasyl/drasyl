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
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses />.
 */
package org.drasyl.plugins;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link PluginOptions}.
 * <p>
 * <b>Should not be used by a {@link DrasylPlugin}. It
 * is only used internally by drasyl.</b>
 */
@SuppressWarnings({ "java:S2440" })
public class DefaultPluginOptions extends PluginOptions {
    public DefaultPluginOptions(Builder builder) {
        super(builder);
    }

    public DefaultPluginOptions(Config options) {
        super(options);
    }

    /**
     * Just returns a default builder.
     *
     * @return default builder
     */
    public static BuilderBase<DefaultPluginOptions, Builder> builder() {
        return new Builder();
    }

    /**
     * Implements default builder behavior for {@link Builder#build()} and {@link
     * Builder#buildEnv()}.
     */
    public static class Builder extends BuilderBase<DefaultPluginOptions, Builder> {
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public DefaultPluginOptions build() {
            return new DefaultPluginOptions(this);
        }

        @Override
        public PluginEnvironment buildEnv() {
            return new PluginEnvironment(build(), AutoloadablePlugin.class);
        }
    }
}
