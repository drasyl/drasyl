/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.arq.gobackn;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Performs the Go-Back-N ARQ receiver protocol.
 * <p>
 * This handler should be used together with {@link GoBackNArqCodec},
 * {@link ByteToGoBackNArqDataCodec} and {@link GoBackNArqReceiverHandler}.
 * <blockquote>
 * <pre>
 *  {@link ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("arq_codec", <b>new {@link GoBackNArqCodec}()</b>);
 *  p.addLast("arq_snd_handler", <b>new {@link GoBackNArqSenderHandler}(150, Duration.ofMillis(100))</b>);
 *  p.addLast("arg_rec_handler", <b>new {@link GoBackNArqReceiverHandler}(Duration.ofMills(100).dividedBy(10))</b>);
 *  p.addLast("buf_codec", <b>new {@link ByteToGoBackNArqDataCodec}()</b>);
 *  ...
 *  p.addLast("handler", new HttpRequestHandler());
 *  </pre>
 * </blockquote>
 */
public class GoBackNArqReceiverHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GoBackNArqReceiverHandler.class);
    private UnsignedInteger nextSequenceNo;
    private final Duration ackClock;
    private ScheduledFuture<?> ackTask;
    private boolean ackRequired;

    /**
     * @param nextSequenceNo the next expected inbound sequence number
     * @param ackClock       the frequency of sending ACKs for received packages
     */
    public GoBackNArqReceiverHandler(final UnsignedInteger nextSequenceNo,
                                     final Duration ackClock) {
        this.nextSequenceNo = nextSequenceNo;
        this.ackClock = ackClock;
    }

    /**
     * @param ackClock the frequency of sending ACKs for received packages
     */
    public GoBackNArqReceiverHandler(final Duration ackClock) {
        this(UnsignedInteger.MIN_VALUE, ackClock);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();

        ackTask(ctx);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            ackTask(ctx);
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
        stopAckTask();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof GoBackNArqData) {
            final GoBackNArqData data = (GoBackNArqData) msg;

            ackRequired = true;

            if (!data.sequenceNo().equals(nextSequenceNo)) {
                LOG.trace("[{}] Got unexpected data {}. Expected {}. Drop it.", ctx.channel().id()::asShortText, () -> data, () -> nextSequenceNo);
                data.release();
            }
            else {
                LOG.trace("[{}] Got expected {}. Pass through.", ctx.channel().id()::asShortText, () -> data);

                // increase sequence no
                nextSequenceNo = nextSequenceNo.safeIncrement();

                // expected sequence no -> pass DATA inbound
                ctx.fireChannelRead(msg);
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Acknowledge received packages. We do this as task to avoid congestion and save bandwidth.
     */
    private void ackTask(final ChannelHandlerContext ctx) {
        if (ackRequired) {
            ackRequired = false;
            // reply with ACK of current inbound index
            ctx.writeAndFlush(new GoBackNArqAck(nextSequenceNo.safeDecrement()));
        }

        ackTask = ctx.executor().schedule(() -> ackTask(ctx), ackClock.toMillis(), MILLISECONDS);
    }

    /**
     * Stops the ACK task.
     */
    private void stopAckTask() {
        if (ackTask != null) {
            ackTask.cancel(true);
        }
    }
}
