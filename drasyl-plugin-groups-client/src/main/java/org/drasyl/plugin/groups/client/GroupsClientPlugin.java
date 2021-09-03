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
package org.drasyl.plugin.groups.client;

import com.typesafe.config.Config;
import org.drasyl.channel.ApplicationMessageCodec;
import org.drasyl.plugin.DrasylPlugin;
import org.drasyl.plugin.PluginEnvironment;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

/**
 * The Groups Membership Client Plugin allows drasyl nodes to connect to membership managers at
 * startup to join groups. The manager can then inform the node about existing memberships as well
 * as leaving and joining nodes.
 */
public class GroupsClientPlugin implements DrasylPlugin {
    public static final String GROUPS_CLIENT_HANDLER = "GROUPS_CLIENT_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientPlugin.class);
    private final GroupsClientConfig config;

    /**
     * This constructor is used by {@link org.drasyl.DrasylConfig} to initialize this plugin.
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
    public void onBeforeStart(final PluginEnvironment environment) {
        LOG.debug("Start Groups Client Plugin with options: {}", config);

        environment.getPipeline().addAfter(environment.getPipeline().context(ApplicationMessageCodec.class).name(), GROUPS_CLIENT_HANDLER, new GroupsClientHandler(config.getGroups(), environment.getIdentity()));
        environment.getPipeline().addBefore(GROUPS_CLIENT_HANDLER, "GROUPS_MANAGER_DECODER", new GroupsServerMessageDecoder());
        environment.getPipeline().addBefore(GROUPS_CLIENT_HANDLER, "GROUPS_CLIENT_ENCODER", new GroupsClientMessageEncoder());
    }

    @Override
    public void onBeforeShutdown(final PluginEnvironment environment) {
        LOG.debug("Stop Groups Client Plugin.");

        environment.getPipeline().remove(GROUPS_CLIENT_HANDLER);
    }
}
