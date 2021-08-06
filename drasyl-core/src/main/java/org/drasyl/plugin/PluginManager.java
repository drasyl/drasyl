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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ServerChannel;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * The {@code PluginManager} notifies all enabled plugins about specific node events (like startup
 * or shutdown).
 */
public class PluginManager {
    private static final Logger LOG = LoggerFactory.getLogger(PluginManager.class);

    /**
     * This method is called first when the {@link org.drasyl.DrasylNode} is started.
     *
     * @param ctx
     */
    public void beforeStart(final ChannelHandlerContext ctx) {
        final ServerChannel channel = (ServerChannel) ctx.channel();
        final DrasylConfig config = channel.attr(CONFIG_ATTR_KEY).get();
        final Identity identity = channel.attr(IDENTITY_ATTR_KEY).get();
        final ChannelPipeline pipeline = channel.pipeline();
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onBeforeStart listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onBeforeStart(environment));
            LOG.debug("All onBeforeStart listeners executed");
        }
    }

    /**
     * This method is called last when the {@link org.drasyl.DrasylNode} is started.
     *
     * @param ctx
     */
    public void afterStart(final ChannelHandlerContext ctx) {
        final ServerChannel channel = (ServerChannel) ctx.channel();
        final DrasylConfig config = channel.attr(CONFIG_ATTR_KEY).get();
        final Identity identity = channel.attr(IDENTITY_ATTR_KEY).get();
        final ChannelPipeline pipeline = channel.pipeline();
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onAfterStart listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onAfterStart(environment));
            LOG.debug("All onAfterStart listeners executed");
        }
    }

    /**
     * This method get called first when the {@link org.drasyl.DrasylNode} is shut down.
     *
     * @param ctx
     */
    public void beforeShutdown(final ChannelHandlerContext ctx) {
        final ServerChannel channel = (ServerChannel) ctx.channel();
        final DrasylConfig config = channel.attr(CONFIG_ATTR_KEY).get();
        final Identity identity = channel.attr(IDENTITY_ATTR_KEY).get();
        final ChannelPipeline pipeline = channel.pipeline();
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onBeforeShutdown listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onBeforeShutdown(environment));
            LOG.debug("All onBeforeShutdown listeners executed");
        }
    }

    /**
     * This method get called last when the {@link org.drasyl.DrasylNode} is shut down.
     *
     * @param ctx
     */
    public void afterShutdown(final ChannelHandlerContext ctx) {
        final ServerChannel channel = (ServerChannel) ctx.channel();
        final DrasylConfig config = channel.attr(CONFIG_ATTR_KEY).get();
        final Identity identity = channel.attr(IDENTITY_ATTR_KEY).get();
        final ChannelPipeline pipeline = channel.pipeline();
        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onAfterShutdown listeners for all plugins...");
            final PluginEnvironment environment = new PluginEnvironment(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onAfterShutdown(environment));
            LOG.debug("All onAfterShutdown listeners executed");
        }
    }
}
