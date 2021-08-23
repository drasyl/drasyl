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

import com.typesafe.config.ConfigFactory;
import io.netty.channel.ChannelPipeline;
import org.drasyl.plugin.PluginEnvironment;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_BIND_HOST;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_BIND_PORT;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.API_ENABLED;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.DATABASE_URI;
import static org.drasyl.plugin.groups.manager.GroupsManagerConfig.GROUPS;
import static org.drasyl.plugin.groups.manager.GroupsManagerPlugin.GROUPS_MANAGER_HANDLER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsManagerPluginTest {
    @Mock
    private GroupsManagerConfig groupsManagerConfig;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private PluginEnvironment env;
    @Mock
    private DatabaseAdapter databaseAdapter;

    @Nested
    class OnBeforeStart {
        @Test
        void shouldInitDB() throws DatabaseException {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(groupsManagerConfig, databaseAdapter);
            final Group group = Group.of("group", "secret", (byte) 0, Duration.ofSeconds(60));
            when(groupsManagerConfig.getGroups()).thenReturn(Map.of(group.getName(), group));
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onBeforeStart(env);

            verify(databaseAdapter).addGroup(group);
        }

        @Test
        void shouldAddHandlerToPipeline() {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(new GroupsManagerConfig(ConfigFactory.parseMap(Map.of(
                    DATABASE_URI, "",
                    GROUPS, Map.of(),
                    API_ENABLED, false,
                    API_BIND_HOST, "0.0.0.0",
                    API_BIND_PORT, 8080))), databaseAdapter);
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onBeforeStart(env);

            verify(pipeline).addAfter(any(), eq(GROUPS_MANAGER_HANDLER), isA(GroupsManagerHandler.class));
        }
    }

    @Nested
    class OnBeforeShutdown {
        @Test
        void shouldRemoveHandlerFromPipeline() throws DatabaseException {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(groupsManagerConfig, databaseAdapter);
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onBeforeShutdown(env);

            verify(pipeline).remove(GROUPS_MANAGER_HANDLER);
            verify(databaseAdapter).close();
        }
    }
}
