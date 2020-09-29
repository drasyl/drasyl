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
 * This interface is implemented by all drasyl plugins.
 * <br>
 * <b>Every drasyl plugin must implement a constructor with the {@link Config} as only
 * parameter.</b>
 */
public interface DrasylPlugin {
    /**
     * This method gets called before the drasyl node is started. <br />
     * <b>At this point, no communication channel is alive.</b>
     *
     * @param environment the plugin environment
     */
    default void onBeforeStart(final PluginEnvironment environment) {
        // do nothing
    }

    /**
     * This method gets called after the drasyl node was started.
     *
     * @param environment the plugin environment
     */
    default void onAfterStart(final PluginEnvironment environment) {
        // do nothing
    }

    /**
     * This method get called before the drasyl node is shut down.
     *
     * @param environment the plugin environment
     */
    default void onBeforeShutdown(final PluginEnvironment environment) {
        // do nothing
    }

    /**
     * This method gets called after the drasyl node was shut down. <br />
     * <b>At this point, no communication channel is alive.</b>
     *
     * @param environment the plugin environment
     */
    default void onAfterShutdown(final PluginEnvironment environment) {
        // do nothing
    }
}