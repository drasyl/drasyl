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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * This handler prevent any direct path establishment to the given peer. Must be placed before
 * {@link TraversingInternetDiscoveryChildrenHandler} in the
 * {@link io.netty.channel.ChannelPipeline}, as this handler will suppress any messages used for
 * direct path establishment.
 */
public class NoDirectPathHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NoDirectPathHandler.class);
    private final IdentityPublicKey peer;

    public NoDirectPathHandler(final IdentityPublicKey peer) {
        this.peer = requireNonNull(peer);
    }

    @Override
    public void write(ChannelHandlerContext ctx,
                      Object msg,
                      ChannelPromise promise) throws Exception {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof HelloMessage && peer.equals(((HelloMessage) ((InetAddressedMessage<?>) msg).content()).getRecipient())) {
            // hello message to peer
            ReferenceCountUtil.release(msg);
        }
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage && peer.equals(((AcknowledgementMessage) ((InetAddressedMessage<?>) msg).content()).getRecipient())) {
            // hello message to peer
            ReferenceCountUtil.release(msg);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof HelloMessage && peer.equals(((HelloMessage) ((InetAddressedMessage<?>) msg).content()).getSender())) {
            // hello message from peer
            ReferenceCountUtil.release(msg);
        }
        else if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof AcknowledgementMessage && peer.equals(((AcknowledgementMessage) ((InetAddressedMessage<?>) msg).content()).getSender())) {
            // hello message from peer
            ReferenceCountUtil.release(msg);
        }
        else if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof UniteMessage && peer.equals(((UniteMessage) ((InetAddressedMessage<?>) msg).content()).getAddress())) {
            // unite message for peer
            ReferenceCountUtil.release(msg);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }
}
