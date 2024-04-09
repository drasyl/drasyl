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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Queue;

import static java.util.Objects.requireNonNull;

/**
 * This handler passes messages from the {@link io.netty.channel.socket.DatagramChannel} to the
 * {@link org.drasyl.channel.DrasylServerChannel}'s context.
 */
@UnstableApi
public class UdpServerToDrasylHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(UdpServerToDrasylHandler.class);
    private final ChannelHandlerContext drasylCtx;
    private final Queue<Object> outboundBuffer = PlatformDependent.newMpscQueue();

    public UdpServerToDrasylHandler(final ChannelHandlerContext drasylCtx) {
        this.drasylCtx = requireNonNull(drasylCtx);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        LOG.trace("Read Datagram {}", msg);
        drasylCtx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        drasylCtx.fireChannelReadComplete();
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        releaseOutboundBuffer();

        ctx.fireChannelInactive();
    }

    Queue<Object> outboundBuffer() {
        return outboundBuffer;
    }

    void finishWrite(final ChannelHandlerContext ctx) {
        // check whether the channel is currently reading; if so, we must schedule the event in the
        // event loop to maintain the read/write order.
        if (ctx.executor().inEventLoop()) {
            finishWrite0(ctx);
        }
        else {
            runFinishWriteTask(ctx);
        }
    }

    private void finishWrite0(final ChannelHandlerContext ctx) {
        if (!outboundBuffer.isEmpty()) {
            writeOutbound(ctx);
        }

    }

    private void runFinishWriteTask(final ChannelHandlerContext ctx) {
        ctx.executor().execute(() -> this.finishWrite0(ctx));
    }

    void writeOutbound(final ChannelHandlerContext ctx) {
        final ChannelPipeline pipeline = ctx.pipeline();
        do {
            final Object toSend = outboundBuffer.poll();
            if (toSend == null) {
                break;
            }
            pipeline.write(toSend).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Outbound message `{}` written to datagram channel failed:", () -> toSend, future::cause);
                }
            });
        } while (true); // TODO: use isWritable?

        // all messages written, fire flush event
        pipeline.flush();
    }

    private void releaseOutboundBuffer() {
        Object msg;
        while ((msg = outboundBuffer.poll()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }
}
