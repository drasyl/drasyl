/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginsHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private DrasylPlugin plugin;

    @BeforeEach
    void setUp() {
        when(config.getPlugins()).thenReturn(Set.of(plugin));
    }

    @Nested
    class ChannelRegistered {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelRegistered(ctx);

            verify(plugin).onBeforeStart(any());
        }
    }

    @Nested
    class ChannelActive {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelActive(ctx);

            verify(plugin).onAfterStart(any());
        }
    }

    @Nested
    class ChannelInactive {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelInactive(ctx);

            verify(plugin).onBeforeShutdown(any());
        }
    }

    @Nested
    class ChannelUnregistered {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelUnregistered(ctx);

            verify(plugin).onAfterShutdown(any());
        }
    }
}
