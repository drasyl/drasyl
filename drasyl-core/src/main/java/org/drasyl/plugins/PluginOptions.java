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

import java.util.Objects;

/**
 * Models the internal options of a {@link DrasylPlugin}. It must be able to produce the {@link
 * PluginOptions} from a {@link Config} and by passing a {@link BuilderBase} into the constructor.
 * <p>
 * Inspired by: <a href="https://ieeexplore.ieee.org/document/8466532">M. M. Chalabine, "Refined
 * Fluent Builder in Java," 2018 IEEE/ACIS 17th International Conference on Computer and Information
 * Science (ICIS), Singapore, 2018, pp. 537-542, doi: 10.1109/ICIS.2018.8466532.</a>
 * </p>
 */
public abstract class PluginOptions {
    private final Config config;

    /**
     * Creates a new {@link PluginOptions} from a given builder.
     *
     * @param builder the builder
     */
    public PluginOptions(BuilderBase<?, ?> builder) {
        this.config = builder.config;
    }

    /**
     * Creates a new {@link PluginOptions} from a given config.
     *
     * @param config the config
     */
    public PluginOptions(Config config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Returns the {@link Config} if the config was built automatically by drasyl, otherwise null.
     *
     * @return {@link Config} or null
     */
    public Config getConfig() {
        return this.config;
    }

    /**
     * Builder implementation for this configuration.
     * <p>
     * Implement your setters here like in the normal builder-pattern. Use {@code T} as return
     * type.
     * </p>
     *
     * @param <B> the class of {@link PluginOptions} implementation
     * @param <T> always the {@link Builder} class
     */
    public abstract static class BuilderBase<B extends PluginOptions, T extends BuilderBase<B, T>> {
        Config config;

        protected abstract T self();

        public T config(Config config) {
            this.config = config;
            return self();
        }

        public abstract B build();

        public abstract PluginEnvironment buildEnv();
    }

    /**
     * Wrapper for the {@link BuilderBase}. Implement this class in all subclasses.
     */
    public abstract static class Builder extends BuilderBase<PluginOptions, Builder> {
        @Override
        protected Builder self() {
            return this;
        }
    }
}
