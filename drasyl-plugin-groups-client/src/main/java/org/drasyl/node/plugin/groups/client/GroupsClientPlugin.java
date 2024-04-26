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
package org.drasyl.node.plugin.groups.client;

import com.typesafe.config.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.handler.plugin.DrasylPlugin;
import org.drasyl.node.handler.plugin.PluginEnvironment;
import org.drasyl.node.handler.serialization.MessageSerializer;
import org.drasyl.node.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

/**
 * The Groups Membership Client Plugin allows drasyl nodes to connect to membership managers at
 * startup to join groups. The manager can then inform the node about existing memberships as well
 * as leaving and joining nodes.
 */
@UnstableApi
public class GroupsClientPlugin implements DrasylPlugin {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientPlugin.class);
    private final GroupsClientConfig config;

    /**
     * This constructor is used by {@link DrasylConfig} to initialize this plugin.
     *
     * @param config the plugin's portion of the configuration
     */
    public GroupsClientPlugin(final Config config) {
        this(new GroupsClientConfig(config));
    }

    public GroupsClientPlugin(final GroupsClientConfig config) {
        this.config = config;
    }

    @Override
    public void onServerChannelRegistered(final PluginEnvironment env) {
        LOG.debug("Start Groups Client Plugin with options: {}", config);

        env.getPipeline().addLast(new GroupsClientHandler(config.getGroups(), env.getIdentity()));
    }

    @Override
    public void onServerChannelInactive(final PluginEnvironment env) {
        LOG.debug("Stop Groups Client Plugin.");

        env.getPipeline().remove(GroupsClientHandler.class);
    }

    @Override
    public void onChildChannelRegistered(final PluginEnvironment env) {
        env.getPipeline().addBefore(env.getPipeline().context(MessageSerializer.class).name(), null, new GroupsServerMessageDecoder());
        env.getPipeline().addBefore(env.getPipeline().context(MessageSerializer.class).name(), null, new SimpleChannelInboundHandler<GroupsServerMessage>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx,
                                        final GroupsServerMessage msg) {
                ctx.channel().parent().pipeline().fireChannelRead(new OverlayAddressedMessage<>(msg, (DrasylAddress) ctx.channel().localAddress(), (DrasylAddress) ctx.channel().remoteAddress()));
            }
        });
        env.getPipeline().addAfter(env.getPipeline().context(MessageSerializer.class).name(), null, new GroupsClientMessageEncoder());
    }

    @Override
    public void onChildChannelInactive(final PluginEnvironment env) {
        env.getPipeline().remove(GroupsServerMessageDecoder.class);
        env.getPipeline().remove(GroupsClientMessageEncoder.class);
    }
}
