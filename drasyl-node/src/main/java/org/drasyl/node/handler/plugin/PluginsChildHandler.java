/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node.handler.plugin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class PluginsChildHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(PluginsChildHandler.class);
    private final DrasylConfig config;
    private final Identity identity;

    public PluginsChildHandler(final DrasylConfig config, final Identity identity) {
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();

        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onChildChannelRegistered listeners for all plugins...");
            final PluginEnvironment environment = PluginEnvironment.of(config, identity, ctx.channel().pipeline());
            config.getPlugins().forEach(plugin -> plugin.onChildChannelRegistered(environment));
            LOG.debug("All onChildChannelRegistered listeners executed");
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onChildChannelActive listeners for all plugins...");
            final PluginEnvironment environment = PluginEnvironment.of(config, identity, ctx.channel().pipeline());
            config.getPlugins().forEach(plugin -> plugin.onChildChannelActive(environment));
            LOG.debug("All onChildChannelActive listeners executed");
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onChildChannelInactive listeners for all plugins...");
            final PluginEnvironment environment = PluginEnvironment.of(config, identity, ctx.channel().pipeline());
            config.getPlugins().forEach(plugin -> plugin.onChildChannelInactive(environment));
            LOG.debug("All onChildChannelInactive listeners executed");
        }
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();

        final ChannelPipeline pipeline = ctx.channel().pipeline();

        if (!config.getPlugins().isEmpty()) {
            LOG.debug("Execute onChildChannelUnregistered listeners for all plugins...");
            final PluginEnvironment environment = PluginEnvironment.of(config, identity, pipeline);
            config.getPlugins().forEach(plugin -> plugin.onChildChannelUnregistered(environment));
            LOG.debug("All onChildChannelUnregistered listeners executed");
        }
    }
}
