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
package org.drasyl.plugin;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

/**
 * The {@code PluginManager} notifies all enabled plugins about specific node events (like startup
 * or shutdown).
 */
public class PluginManager {
    private static final Logger LOG = LoggerFactory.getLogger(PluginManager.class);
    private final DrasylConfig config;
    private final Identity identity;
    private final Pipeline pipeline;

    public PluginManager(final DrasylConfig config,
                         final Identity identity,
                         final Pipeline pipeline) {
        this.config = config;
        this.identity = identity;
        this.pipeline = pipeline;
    }

    /**
     * This method is called first when the {@link org.drasyl.DrasylNode} is started.
     */
    public void beforeStart() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onBeforeStart listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onBeforeStart(environment));
            LOG.debug("All onBeforeStart listeners executed");
        }
    }

    /**
     * This method is called last when the {@link org.drasyl.DrasylNode} is started.
     */
    public void afterStart() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onAfterStart listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onAfterStart(environment));
            LOG.debug("All onAfterStart listeners executed");
        }
    }

    /**
     * This method get called first when the {@link org.drasyl.DrasylNode} is shut down.
     */
    public void beforeShutdown() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onBeforeShutdown listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onBeforeShutdown(environment));
            LOG.debug("All onBeforeShutdown listeners executed");
        }
    }

    /**
     * This method get called last when the {@link org.drasyl.DrasylNode} is shut down.
     */
    public void afterShutdown() {
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onAfterShutdown listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onAfterShutdown(environment));
            LOG.debug("All onAfterShutdown listeners executed");
        }
    }
}
