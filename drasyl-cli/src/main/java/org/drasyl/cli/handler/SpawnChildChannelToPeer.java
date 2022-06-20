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
package org.drasyl.cli.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * This handler spawns the creation of {@link DrasylChannel}s to given peers once the server channel
 * becomes active.
 */
public class SpawnChildChannelToPeer extends ChannelInboundHandlerAdapter {
    private final Set<DrasylAddress> remoteAddresses;

    public SpawnChildChannelToPeer(final Set<DrasylAddress> remoteAddresses) {
        this.remoteAddresses = requireNonNull(remoteAddresses);
    }

    public SpawnChildChannelToPeer(final IdentityPublicKey remoteAddress) {
        this(Set.of(remoteAddress));
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            spawnChannels(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
        spawnChannels(ctx);
    }

    private void spawnChannels(final ChannelHandlerContext ctx) {
        if (ctx.channel().isOpen()) {
            for (final DrasylAddress remoteAddress : remoteAddresses) {
                final DrasylChannel childChannel = new DrasylChannel((DrasylServerChannel) ctx.channel(), remoteAddress);
                ctx.fireChannelRead(childChannel);
            }
        }
        ctx.pipeline().remove(this);
    }
}
