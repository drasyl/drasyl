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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.remote.protocol.ChunkMessage;
import org.drasyl.remote.protocol.RemoteMessage;


/**
 * This handler filters out all messages received from other networks.
 */
@SuppressWarnings("java:S110")
public final class OtherNetworkFilter extends SimpleChannelInboundHandler<AddressedMessage<?, ?>> {
    private final int networkId;

    public OtherNetworkFilter(final int networkId) {
        this.networkId = networkId;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<?, ?> msg) throws OtherNetworkException {
        if (msg.message() instanceof RemoteMessage) {
            final RemoteMessage remoteMsg = (RemoteMessage) msg.message();
            if (remoteMsg instanceof ChunkMessage || networkId == remoteMsg.getNetworkId()) {
                ctx.fireChannelRead(msg.retain());
            }
            else {
                throw new OtherNetworkException(remoteMsg);
            }
        }
        else {
            ctx.fireChannelRead(msg.retain());
        }
    }

    /**
     * Signals that a message was received from another network and was dropped.
     */
    public static class OtherNetworkException extends Exception {
        public OtherNetworkException(final RemoteMessage msg) {
            super("Message `" + msg.getNonce() + "` from other network dropped.");
        }
    }
}
