/*
 * Copyright (c) 2020-2024.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.node.handler.plugin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.EmbeddedNode;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.event.MessageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static test.util.IdentityTestUtil.ID_1;

@ExtendWith(MockitoExtension.class)
class PluginsIT {
    @Mock
    private static MessageEvent event1;
    @Mock
    private static MessageEvent event2;
    private DrasylConfig config;
    private EmbeddedNode node;

    @BeforeEach
    void setup() throws DrasylException {
        config = DrasylConfig.newBuilder()
                .plugins(Set.of(new TestPlugin(ConfigFactory.empty())))
                .build();

        config = DrasylConfig.newBuilder(config)
                .identity(ID_1)
                .remoteExposeEnabled(false)
                .remoteSuperPeerEnabled(false)
                .remoteBindPort(0)
                .remoteTcpFallbackEnabled(false)
                .remoteLocalNetworkDiscoveryEnabled(false)
                .remoteLocalHostDiscoveryEnabled(false)
                .build();

        node = new EmbeddedNode(config);
    }

    @AfterEach
    void shutdown() {
        if (node != null) {
            node.shutdown();
        }
    }

    @Test
    void pluginShouldBeLoadedAndAlsoCorrespondingHandlers() {
        node.awaitStarted();

        await("event1").untilAsserted(() -> assertEquals(event1, node.readEvent()));
        await("event2").untilAsserted(() -> assertEquals(event2, node.readEvent()));
    }

    public static class TestPlugin implements DrasylPlugin {
        public TestPlugin(final Config config) {
            // do nothing
        }

        @Override
        public void onAfterStart(final PluginEnvironment environment) {
            environment.getPipeline().addFirst("TestHandler", new ChannelHandlerAdapter() {
                @Override
                public void handlerAdded(final ChannelHandlerContext ctx) {
                    ctx.fireUserEventTriggered(event1);
                }
            });
            environment.getPipeline().fireUserEventTriggered(event2);
        }
    }
}
