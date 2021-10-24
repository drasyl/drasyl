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

        await().untilAsserted(() -> assertEquals(event1, node.readEvent()));
        await().untilAsserted(() -> assertEquals(event2, node.readEvent()));
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
