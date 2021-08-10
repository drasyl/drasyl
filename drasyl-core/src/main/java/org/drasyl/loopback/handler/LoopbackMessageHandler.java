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
package org.drasyl.loopback.handler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.AddressedMessage;

import java.net.SocketAddress;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * This handler converts outgoing messages addressed to the local node to incoming messages
 * addressed to the local node.
 */
@Sharable
public class LoopbackMessageHandler extends ChannelOutboundHandlerAdapter {
    private final boolean started;

    LoopbackMessageHandler(final boolean started) {
        this.started = started;
    }

    public LoopbackMessageHandler() {
        this(false);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        if (msg instanceof AddressedMessage) {
            if (started && ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().equals(((AddressedMessage<?, ?>) msg).address())) {
                ctx.fireChannelRead(new AddressedMessage<>(((AddressedMessage<?, ?>) msg).message(), (SocketAddress) ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()));
            }
            else {
                ctx.writeAndFlush(msg, promise);
            }
        }
    }
}
