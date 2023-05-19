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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.noop.NoopDiscardHandler;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This handler tries to maintain a direct path to the given peer. Must be placed behind
 * {@link TraversingInternetDiscoveryChildrenHandler} in the
 * {@link io.netty.channel.ChannelPipeline}, as this handler will send some NOOP traffic to trigger
 * direct link establishments.
 */
public class DirectPathHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DirectPathHandler.class);
    private final IdentityPublicKey peer;
    private ScheduledFuture<?> retryTask;

    public DirectPathHandler(final IdentityPublicKey peer) {
        this.peer = requireNonNull(peer);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            triggerDirectPathEstablishment(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelTask();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        if (retryTask == null) {
            triggerDirectPathEstablishment(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancelTask();

        ctx.fireChannelInactive();
    }

    private void triggerDirectPathEstablishment(ChannelHandlerContext ctx) {
        // TODO: es wäre besser, wenn wir ein Path-Event bekommen würden und dann reaktiv reagieren könnten
        if (!((DrasylServerChannel) ctx.channel()).isDirectPathPresent(peer)) {
            LOG.debug("No direct path to `{}` present. Send NOOP message to trigger direct path establishment.", peer);

            final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NoopDiscardHandler.NOOP_MAGIC_NUMBER);
            final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(byteBuf, peer, (DrasylAddress) ctx.channel().localAddress());
            ctx.writeAndFlush(msg);
        }
        else {
            LOG.debug("Direct path to `{}` present. Nothing to do.", peer);
        }

        if (retryTask != null) {
            retryTask.cancel(false);
        }
        retryTask = ctx.executor().schedule(() -> triggerDirectPathEstablishment(ctx), 5_000, MILLISECONDS);
    }

    private void cancelTask() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }
}
