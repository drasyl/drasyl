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
package org.drasyl.cli.sdo.handler.policy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.cli.sdo.config.LinkPolicy;
import org.drasyl.cli.sdo.handler.NetworkConfigHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.cli.sdo.config.Policy.PolicyState.ABSENT;
import static org.drasyl.handler.noop.NoopDiscardHandler.NOOP_MAGIC_NUMBER;

public class LinkPolicyHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LinkPolicyHandler.class);
    private final LinkPolicy policy;

    public LinkPolicyHandler(final LinkPolicy policy) {
        this.policy = requireNonNull(policy);
    }
    private ScheduledFuture<?> retryTask;
    private boolean directPathEstablished = false;

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            directPathEstablished = ((DrasylServerChannel) ctx.channel()).isDirectPathPresent(policy.peer());
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
            directPathEstablished = ((DrasylServerChannel) ctx.channel()).isDirectPathPresent(policy.peer());
            triggerDirectPathEstablishment(ctx);
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelTask();

        ctx.fireChannelInactive();
    }

    private void triggerDirectPathEstablishment(final ChannelHandlerContext ctx) {
//        LOG.error("triggerDirectPathEstablishment {}", policy.peer());
        final boolean directPathPresentNow = ((DrasylServerChannel) ctx.channel()).isDirectPathPresent(policy.peer());
//        LOG.error("directPathPresentNow = {} {}", directPathPresentNow, policy.peer());
//        LOG.error("policy = {} {}", policy, policy.peer());

        if (directPathPresentNow != directPathEstablished) {
            if (directPathPresentNow) {
                NetworkConfigHandler.LOG.trace("Direct path to `{}` established.", policy.peer());
                policy.setCurrentState(policy.desiredState());
            }
            else {
                NetworkConfigHandler.LOG.trace("Direct path to `{}` lost. Try to establish it again.", policy.peer());
                policy.setCurrentState(ABSENT);
            }
            directPathEstablished = directPathPresentNow;
        }

        if (!directPathPresentNow) {
            LOG.error("No direct path to `{}` present. Send NOOP message to trigger direct path establishment.", policy.peer());

            final ByteBuf byteBuf = ctx.alloc().buffer(Long.BYTES).writeLong(NOOP_MAGIC_NUMBER);
            final OverlayAddressedMessage<ByteBuf> msg = new OverlayAddressedMessage<>(byteBuf, policy.peer(), (DrasylAddress) ctx.channel().localAddress());
            ctx.writeAndFlush(msg).addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.cause() != null) {
                    LOG.warn("Error sending NOOP: ", channelFuture.cause());
                }
            });
        }
        else {
            LOG.trace("Direct path to `{}` present. Nothing to do.", policy.peer());
            policy.setCurrentState(policy.desiredState());
        }

        if (retryTask != null) {
            retryTask.cancel(false);
        }
//        LOG.error("ctx.executor().schedule {}", policy.peer());
        retryTask = ctx.executor().schedule(() -> triggerDirectPathEstablishment(ctx), 5_000, MILLISECONDS);
    }

    private void cancelTask() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

//    @Override
//    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
//        if (msg instanceof OverlayAddressedMessage<?> && ((OverlayAddressedMessage<?>) msg).content() instanceof ByteBuf) {
//            //LOG.error("{}", ByteBufUtil.hexDump((ByteBuf) ((OverlayAddressedMessage<?>) msg).content()));
//            ctx.write(msg, promise);
//        }
//        else {
//            ctx.write(msg, promise);
//        }
//    }
}
