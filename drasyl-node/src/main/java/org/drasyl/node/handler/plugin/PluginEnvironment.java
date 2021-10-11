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

import com.google.auto.value.AutoValue;
import io.netty.channel.ChannelPipeline;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;

/**
 * Models environment information of a {@link DrasylPlugin} that are required by the plugin.
 */
@SuppressWarnings("java:S118")
@AutoValue
public abstract class PluginEnvironment {
    public abstract DrasylConfig getConfig();

    public abstract Identity getIdentity();

    public abstract ChannelPipeline getPipeline();

    public static PluginEnvironment of(final DrasylConfig config,
                                       final Identity identity,
                                       final ChannelPipeline pipeline) {
        return new AutoValue_PluginEnvironment(config, identity, pipeline);
    }
}
