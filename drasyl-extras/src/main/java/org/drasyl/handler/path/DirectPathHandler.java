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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.noop.NoopDiscardHandler;
import org.drasyl.handler.remote.ApplicationMessageToPayloadCodec;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

/**
 * This handler attempts to establish and maintain a direct path to a given peer. This is achieved
 * by periodically sending no-op messages to the given peer which result in
 * {@link TraversingInternetDiscoveryChildrenHandler} establishing a direct path. Must be placed
 * after {@link ApplicationMessageToPayloadCodec}. The remote peer should have the
 * {@link NoopDiscardHandler} in its pipeline, otherwise no-op messages will be delivered to the
 * peer's application.
 */
public class DirectPathHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DirectPathHandler.class);
    private final IdentityPublicKey peer;
    private final Duration duration;
    private ScheduledFuture<?> retryTask;
    private boolean directPathEstablished;

    public DirectPathHandler(final IdentityPublicKey peer, final Duration duration) {
        this.peer = requireNonNull(peer);
        this.duration = requireNonNull(duration);
    }

    public DirectPathHandler(final IdentityPublicKey peer) {
        this(peer, ofSeconds(5));
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            directPathEstablished = ((DrasylServerChannel) ctx.channel()).isDirectPathPresent(peer);
            triggerDirectPathEstablishment(ctx);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        cancelTask();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        if (retryTask == null) {
            directPathEstablished = ((DrasylServerChannel) ctx.channel()).isDirectPathPresent(peer);
            triggerDirectPathEstablishment(ctx);
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelTask();

        ctx.fireChannelInactive();
    }

    private void triggerDirectPathEstablishment(final ChannelHandlerContext ctx) {
        final boolean directPathEstablishedNow = ((DrasylServerChannel) ctx.channel()).isDirectPathPresent(peer);

        if (directPathEstablishedNow != directPathEstablished) {
            if (directPathEstablishedNow) {
                LOG.debug("Direct path to `{}` established.", peer);
            }
            else {
                LOG.debug("Direct path to `{}` lost. Try to establish it again.", peer);
            }
            directPathEstablished = directPathEstablishedNow;
        }

        if (!directPathEstablishedNow) {
            LOG.debug("No direct path to `{}` present. Send NOOP message to trigger direct path establishment.", peer);

            final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NOOP_MAGIC_NUMBER);
            final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(byteBuf, peer, (DrasylAddress) ctx.channel().localAddress());
            ctx.writeAndFlush(msg).addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.cause() != null) {
                    LOG.warn("Error sending NOOP: ", channelFuture.cause());
                }
            });
        }
        else {
            LOG.debug("Direct path to `{}` present. Nothing to do.", peer);
        }

        if (retryTask != null) {
            retryTask.cancel(false);
        }
        retryTask = ctx.executor().schedule(() -> triggerDirectPathEstablishment(ctx), duration.toMillis(), MILLISECONDS);
    }

    private void cancelTask() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }
}
