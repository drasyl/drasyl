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
package org.drasyl.plugin.groups.manager;

import com.typesafe.config.Config;
import org.drasyl.channel.ApplicationMessageCodec;
import org.drasyl.plugin.DrasylPlugin;
import org.drasyl.plugin.PluginEnvironment;
import org.drasyl.plugin.groups.client.GroupsClientMessageDecoder;
import org.drasyl.plugin.groups.client.GroupsServerMessageEncoder;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapterManager;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Starting point for the groups master plugin.
 */
public class GroupsManagerPlugin implements DrasylPlugin {
    public static final String GROUPS_MANAGER_HANDLER = "GROUPS_MANAGER_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(GroupsManagerPlugin.class);
    private final GroupsManagerConfig config;
    private DatabaseAdapter database;
    private GroupsManagerApi api;

    GroupsManagerPlugin(final GroupsManagerConfig config,
                        final DatabaseAdapter database) {
        this.config = requireNonNull(config);
        this.database = database;
    }

    public GroupsManagerPlugin(final GroupsManagerConfig config) {
        this(config, null);
    }

    public GroupsManagerPlugin(final Config config) {
        this(new GroupsManagerConfig(config));
    }

    @Override
    public void onBeforeStart(final PluginEnvironment env) {
        try {
            // init database
            if (database == null) {
                database = DatabaseAdapterManager.initAdapter(config.getDatabaseUri());
            }

            for (final Map.Entry<String, Group> entry : config.getGroups().entrySet()) {
                final String name = entry.getKey();
                final Group group = entry.getValue();
                if (!database.addGroup(group)) {
                    LOG.debug("Group `{}` already exists.", name);
                }
                else {
                    LOG.debug("Group `{}` was added.", name);
                }
            }

            env.getPipeline().addAfter(env.getPipeline().context(ApplicationMessageCodec.class).name(), GROUPS_MANAGER_HANDLER, new GroupsManagerHandler(database));
            env.getPipeline().addBefore(GROUPS_MANAGER_HANDLER, "GROUPS_MANAGER_ENCODER", new GroupsServerMessageEncoder());
            env.getPipeline().addBefore(GROUPS_MANAGER_HANDLER, "GROUPS_CLIENT_DECODER", new GroupsClientMessageDecoder());
            LOG.debug("Groups Manager Plugin was started with options: {}", config);
        }
        catch (final DatabaseException e) {
            LOG.error("Database Error: ", e);
        }
    }

    @Override
    public void onAfterStart(final PluginEnvironment env) {
        // start api
        if (config.isApiEnabled()) {
            api = new GroupsManagerApi(config, database);
            api.start();
        }
    }

    @Override
    public void onBeforeShutdown(final PluginEnvironment env) {
        if (api != null) {
            api.shutdown();
        }

        env.getPipeline().remove(GROUPS_MANAGER_HANDLER);
        try {
            database.close();
            database = null;
        }
        catch (final DatabaseException e) {
            LOG.warn("Error occurred during closing the groups database: ", e);
        }

        LOG.debug("Groups Manager Plugin was stopped.");
    }
}
