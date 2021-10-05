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
package org.drasyl.handler.plugin;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ServerChannel;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginManagerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelHandlerContext ctx;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerChannel serverChannel;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @SuppressWarnings("unused")
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @InjectMocks
    private PluginManager underTest;

    @BeforeEach
    void setUp() {
        when(ctx.channel()).thenReturn(serverChannel);
    }

    @Nested
    class BeforeStart {
        @Test
        void shouldCallOnBeforeStartOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(ImmutableSet.of(plugin));

            underTest.beforeStart(ctx);

            verify(plugin).onBeforeStart(any(PluginEnvironment.class));
        }
    }

    @Nested
    class AfterStart {
        @Test
        void shouldCallOnAfterStartOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(ImmutableSet.of(plugin));

            underTest.afterStart(ctx);

            verify(plugin).onAfterStart(any(PluginEnvironment.class));
        }
    }

    @Nested
    class BeforeShutdown {
        @Test
        void shouldCallOnBeforeShutdownOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(ImmutableSet.of(plugin));

            underTest.beforeShutdown(ctx);

            verify(plugin).onBeforeShutdown(any(PluginEnvironment.class));
        }
    }

    @Nested
    class AfterShutdown {
        @Test
        void shouldCallOnAfterShutdownOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(ImmutableSet.of(plugin));

            underTest.afterShutdown(ctx);

            verify(plugin).onAfterShutdown(any(PluginEnvironment.class));
        }
    }
}
