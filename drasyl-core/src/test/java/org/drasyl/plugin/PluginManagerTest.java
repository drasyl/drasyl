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
package org.drasyl.plugin;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginManagerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Pipeline pipeline;
    @Mock
    private Identity identity;
    @InjectMocks
    private PluginManager underTest;

    @Nested
    class BeforeStart {
        @Test
        void shouldCallOnBeforeStartOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(Set.of(plugin));

            underTest.beforeStart();

            verify(plugin).onBeforeStart(new PluginEnvironment(config, identity, pipeline));
        }
    }

    @Nested
    class AfterStart {
        @Test
        void shouldCallOnAfterStartOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(Set.of(plugin));

            underTest.afterStart();

            verify(plugin).onAfterStart(new PluginEnvironment(config, identity, pipeline));
        }
    }

    @Nested
    class BeforeShutdown {
        @Test
        void shouldCallOnBeforeShutdownOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(Set.of(plugin));

            underTest.beforeShutdown();

            verify(plugin).onBeforeShutdown(new PluginEnvironment(config, identity, pipeline));
        }
    }

    @Nested
    class AfterShutdown {
        @Test
        void shouldCallOnAfterShutdownOfEveryPlugin(@Mock final DrasylPlugin plugin) {
            when(config.getPlugins()).thenReturn(Set.of(plugin));

            underTest.afterShutdown();

            verify(plugin).onAfterShutdown(new PluginEnvironment(config, identity, pipeline));
        }
    }
}
