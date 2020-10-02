package org.drasyl.plugins;

import org.drasyl.DrasylConfig;
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

        @Test
        void notSameBecauseOfDifferentConfig() {
            final PluginEnvironment environment1 = new PluginEnvironment(DrasylConfig.newBuilder().build(), pipeline);
            final PluginEnvironment environment2 = new PluginEnvironment(DrasylConfig.newBuilder().build(), pipeline);
            final PluginEnvironment environment3 = new PluginEnvironment(DrasylConfig.newBuilder().serverEnabled(false).build(), pipeline);

            assertEquals(environment1, environment2);
            assertNotEquals(environment2, environment3);
        }
    }

    @Nested
    class HashCode {
        @Mock
        private Pipeline pipeline;

        @Test
        void notSameBecauseOfDifferentConfig() {
            final PluginEnvironment environment1 = new PluginEnvironment(DrasylConfig.newBuilder().build(), pipeline);
            final PluginEnvironment environment2 = new PluginEnvironment(DrasylConfig.newBuilder().build(), pipeline);
            final PluginEnvironment environment3 = new PluginEnvironment(DrasylConfig.newBuilder().serverEnabled(false).build(), pipeline);

            assertEquals(environment1.hashCode(), environment2.hashCode());
            assertNotEquals(environment2.hashCode(), environment3.hashCode());
        }
    }
}