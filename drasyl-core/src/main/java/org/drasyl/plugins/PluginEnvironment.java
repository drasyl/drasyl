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

import com.typesafe.config.Config;

/**
 * Models environment information of a {@link DrasylPlugin} that are required by the plugin or the
 * {@link PluginManager} to automatically load the plugin.
 */
public class PluginEnvironment {
    private final PluginOptions options;
    private final Class<? extends AutoloadablePlugin> clazz;

    public PluginEnvironment(Config options,
                             Class<? extends AutoloadablePlugin> clazz) {
        this(new DefaultPluginOptions(options), clazz);
    }

    public PluginEnvironment(PluginOptions options,
                             Class<? extends AutoloadablePlugin> clazz) {
        this.options = options;
        this.clazz = clazz;
    }

    /**
     * Returns the {@link PluginOptions} of the corresponding plugin.
     *
     * @return the {@link PluginOptions} of the corresponding plugin
     */
    public PluginOptions getOptions() {
        return options;
    }

    /**
     * Returns the class of the corresponding plugin.
     *
     * @return the class of the corresponding plugin
     */
    public Class<? extends AutoloadablePlugin> getClazz() {
        return clazz;
    }
}