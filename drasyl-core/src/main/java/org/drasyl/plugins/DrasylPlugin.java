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

import org.drasyl.pipeline.Handler;

import java.util.List;

/**
 * This interface is implemented by all drasyl plugins.
 */
public interface DrasylPlugin {
    /**
     * This method gets called when the plugin is added to drasyl and adds the given {@link
     * Handler}s to the corresponding {@link org.drasyl.pipeline.Pipeline}. If you want to add your
     * {@link Handler}s manually, return an empty list.
     *
     * @return a sorted list of {@link Handler} that should be added to the {@link
     * org.drasyl.pipeline.Pipeline}
     */
    List<Handler> getHandler();

    /**
     * The plugin name must be unique.
     *
     * @return the plugin name
     */
    String name();

    /**
     * @return a description of this plugin
     */
    String description();

    /**
     * This method gets called when the plugin was removed from drasyl.
     */
    void onRemove();

    /**
     * This method gets called when the plugin was added to drasyl.
     */
    void onAdded();
}