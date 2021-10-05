/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.plugin;

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
