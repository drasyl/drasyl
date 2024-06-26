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
package org.drasyl.node.plugin.groups.manager;

import com.typesafe.config.ConfigFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.drasyl.node.handler.plugin.PluginEnvironment;
import org.drasyl.node.plugin.groups.manager.data.Group;
import org.drasyl.node.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.node.plugin.groups.manager.database.DatabaseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
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
    class OnServerChannelRegistered {
        @Test
        void shouldInitDB(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) throws DatabaseException {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(groupsManagerConfig, databaseAdapter);
            final Group group = Group.of("group", "secret", (byte) 0, Duration.ofSeconds(60));
            when(groupsManagerConfig.getGroups()).thenReturn(Map.of(group.getName(), group));
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onServerChannelRegistered(env);

            verify(databaseAdapter).addGroup(group);
        }

        @Test
        void shouldAddHandlerToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(new GroupsManagerConfig(ConfigFactory.parseMap(Map.of(
                    GroupsManagerConfig.DATABASE_URI, "",
                    GroupsManagerConfig.GROUPS, Map.of(),
                    GroupsManagerConfig.API_ENABLED, false,
                    GroupsManagerConfig.API_BIND_HOST, "0.0.0.0",
                    GroupsManagerConfig.API_BIND_PORT, 8080))), databaseAdapter);
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onServerChannelRegistered(env);

            verify(pipeline).addLast(isA(GroupsManagerHandler.class));
        }
    }

    @Nested
    class OnServerChannelInactive {
        @Test
        void shouldRemoveHandlerFromPipeline() throws DatabaseException {
            final GroupsManagerPlugin plugin = new GroupsManagerPlugin(groupsManagerConfig, databaseAdapter);
            when(env.getPipeline()).thenReturn(pipeline);

            plugin.onServerChannelInactive(env);

            verify(databaseAdapter).close();
        }
    }
}
