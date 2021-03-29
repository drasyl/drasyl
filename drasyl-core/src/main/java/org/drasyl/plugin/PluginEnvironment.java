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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Models environment information of a {@link DrasylPlugin} that are required by the plugin.
 */
public class PluginEnvironment {
    private final DrasylConfig config;
    private final Identity identity;
    private final Pipeline pipeline;

    public PluginEnvironment(final DrasylConfig config,
                             final Identity identity,
                             final Pipeline pipeline) {
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        this.pipeline = requireNonNull(pipeline);
    }

    public DrasylConfig getConfig() {
        return config;
    }

    public Identity getIdentity() {
        return identity;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PluginEnvironment that = (PluginEnvironment) o;
        return Objects.equals(config, that.config) &&
                Objects.equals(pipeline, that.pipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, pipeline);
    }
}
