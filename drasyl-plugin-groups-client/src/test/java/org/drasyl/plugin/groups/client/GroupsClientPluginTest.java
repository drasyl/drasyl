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

import io.netty.channel.ChannelPipeline;
import org.drasyl.DrasylConfig;
import org.drasyl.plugin.PluginEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.plugin.groups.client.GroupsClientPlugin.GROUPS_CLIENT_HANDLER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientPluginTest {
    @Mock
    private GroupsClientConfig groupsClientConfig;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private DrasylConfig config;
    @Mock
    private PluginEnvironment env;

    @Test
    void shouldAddHandlerToPipeline() {
        final GroupsClientPlugin plugin = new GroupsClientPlugin(groupsClientConfig);
        when(env.getPipeline()).thenReturn(pipeline);

        plugin.onBeforeStart(env);

        verify(pipeline).addLast(eq(GROUPS_CLIENT_HANDLER), isA(GroupsClientHandler.class));
    }

    @Test
    void shouldRemoveHandlerFromPipeline() {
        final GroupsClientPlugin plugin = new GroupsClientPlugin(groupsClientConfig);
        when(env.getPipeline()).thenReturn(pipeline);

        plugin.onBeforeShutdown(env);

        verify(pipeline).remove(GROUPS_CLIENT_HANDLER);
    }

    @Test
    void shouldDoNothingOnNotUsedEvents() {
        final GroupsClientPlugin plugin = new GroupsClientPlugin(groupsClientConfig);

        plugin.onAfterShutdown(env);

        verifyNoInteractions(pipeline);
        verifyNoInteractions(config);
        verifyNoInteractions(env);
        verifyNoInteractions(groupsClientConfig);
    }
}
