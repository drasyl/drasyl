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

import io.netty.channel.ChannelPipeline;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class PluginEnvironmentTest {
    @Nested
    class Equals {
        @Mock
        private ChannelPipeline pipeline;
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
        private ChannelPipeline pipeline;
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
