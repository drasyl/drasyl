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
package org.drasyl.remote.handler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.remote.protocol.RemoteMessage;

import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;

/**
 * This handler ensures that {@link RemoteMessage}s do not infinitely circulate in the network. It
 * increments the hop counter of each outgoing message. If the limit of hops is reached, the message
 * is discarded. Otherwise the message can pass.
 */
@Sharable
public final class HopCountGuard extends ChannelOutboundHandlerAdapter {
    public static final HopCountGuard INSTANCE = new HopCountGuard();

    private HopCountGuard() {
        // singleton
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) ((AddressedMessage<?, ?>) msg).message();
            if (remoteMsg.getHopCount().getByte() < ctx.channel().attr(CONFIG_ATTR_KEY).get().getRemoteMessageHopLimit()) {
                ctx.writeAndFlush(new AddressedMessage<>(remoteMsg.incrementHopCount(), ((AddressedMessage<?, ?>) msg).address()));
            }
            else {
                promise.setFailure(new Exception("Hop Count limit has been reached. End of lifespan of message has been reached. Discard message."));
            }
        }
        else {
            ctx.write(msg, promise);
        }
    }
}
