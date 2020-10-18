/*
 * Copyright (c) 2020.
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
package org.drasyl.plugins;

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