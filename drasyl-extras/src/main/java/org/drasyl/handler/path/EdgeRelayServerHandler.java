/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.path;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

public class EdgeRelayServerHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EdgeRelayServerHandler.class);
    private final IdentityPublicKey peerA;
    private final IdentityPublicKey peerB;

    public EdgeRelayServerHandler(final IdentityPublicKey peerA, final IdentityPublicKey peerB) {
        this.peerA = requireNonNull(peerA);
        this.peerB = requireNonNull(peerB);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (isRelayableApplicationMessage(msg)) {
            final InetAddressedMessage<RemoteMessage> inetMsg = (InetAddressedMessage<RemoteMessage>) msg;

            if (!((DrasylServerChannel) ctx.channel()).isDirectPathPresent(inetMsg.content().getRecipient())) {
                // we do not have a direct connection the recipient yet, we should send a noop message to trigger direct link establishment
                final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NOOP_MAGIC_NUMBER);
                final OverlayAddressedMessage<ByteBuf> noopMsg = new OverlayAddressedMessage<>(byteBuf, inetMsg.content().getRecipient(), (DrasylAddress) ctx.channel().localAddress());
                LOG.debug("Send no-op message to `{}` to trigger direct path establishment.", inetMsg.content().getRecipient());
                ctx.channel().write(noopMsg).addListener((ChannelFutureListener) channelFuture -> {
                    if (channelFuture.cause() != null) {
                        LOG.warn("Error sending NOOP: ", channelFuture.cause());
                    }
                });
            }

            LOG.debug("Handle relayable message `{}` for {}.", inetMsg.content().getNonce(), inetMsg.content().getRecipient());
            ctx.channel().writeAndFlush(new OverlayAddressedMessage<>(inetMsg.content(), inetMsg.content().getRecipient()));
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isRelayableApplicationMessage(final Object msg) {
        return msg instanceof InetAddressedMessage &&
                (((InetAddressedMessage<?>) msg).content()) instanceof RemoteMessage &&
                (peerA.equals(((InetAddressedMessage<RemoteMessage>) msg).content().getRecipient()) || peerB.equals(((InetAddressedMessage<RemoteMessage>) msg).content().getRecipient()));
    }
}
