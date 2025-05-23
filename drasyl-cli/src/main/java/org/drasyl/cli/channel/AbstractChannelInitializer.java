/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.rs.RustDrasylServerChannel;
import org.drasyl.cli.handler.SuperPeerTimeoutHandler;

import static org.drasyl.util.Preconditions.requirePositive;

@SuppressWarnings("java:S110")
public abstract class AbstractChannelInitializer extends ChannelInitializer<DrasylServerChannel> {
    private final long onlineTimeoutMillis;

    @SuppressWarnings("java:S107")
    protected AbstractChannelInitializer(final long onlineTimeoutMillis) {
        this.onlineTimeoutMillis = requirePositive(onlineTimeoutMillis);
    }

    @Override
    protected void initChannel(final DrasylServerChannel ch) {
        if (ch instanceof RustDrasylServerChannel) {
            final ChannelPipeline p = ch.pipeline();
            p.addLast(new SuperPeerTimeoutHandler(onlineTimeoutMillis));
        }
    }
}

