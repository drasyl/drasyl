/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin;

import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PluginEnvironmentTest {
    @Nested
    class Equals {
        @Mock
        private Pipeline pipeline;
        @Mock
        private Identity identity;

        @Test
        void notSameBecauseOfDifferentConfig() {
            final PluginEnvironment environment1 = new PluginEnvironment(DrasylConfig.newBuilder().build(), identity, pipeline);
            final PluginEnvironment environment2 = new PluginEnvironment(DrasylConfig.newBuilder().build(), identity, pipeline);
            final PluginEnvironment environment3 = new PluginEnvironment(DrasylConfig.newBuilder().remoteEnabled(false).build(), identity, pipeline);

            assertEquals(environment1, environment2);
            assertNotEquals(environment2, environment3);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Pipeline pipeline;
        @Mock
        private Identity identity;

        @Test
        void notSameBecauseOfDifferentConfig() {
            final PluginEnvironment environment1 = new PluginEnvironment(DrasylConfig.newBuilder().build(), identity, pipeline);
            final PluginEnvironment environment2 = new PluginEnvironment(DrasylConfig.newBuilder().build(), identity, pipeline);
            final PluginEnvironment environment3 = new PluginEnvironment(DrasylConfig.newBuilder().remoteEnabled(false).build(), identity, pipeline);

            assertEquals(environment1.hashCode(), environment2.hashCode());
            assertNotEquals(environment2.hashCode(), environment3.hashCode());
        }
    }
}
