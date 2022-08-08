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
package org.drasyl.handler.arq.gobackn;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Performs the Go-Back-N ARQ protocol.
 * <p>
 * It also updates the {@linkplain Channel#isWritable() writability} of the associated
 * {@link Channel}. This update allows pending write operations to determine the writability.
 * <p>
 * This handler changes the behavior of the {@link io.netty.util.concurrent.Promise}s returned by
 * {@link io.netty.channel.ChannelHandlerContext#write(Object)}: The promise is not complemented
 * until the message's recipient has acknowledged arrival of the message.
 * <p>
 * <b>
 * If you abort the promise, this handler will discard the message. Aborting a promise for a message
 * that is waiting to be acknowledged can cause the sender and receiver to no longer have the same
 * message sequence. This occurs when the acknowledgement of a message is lost and the promise of
 * that message was manually aborted.
 * </b>
 * <p>
 * This handler tries to automatically synchronize the sequence numbers.
 * <p>
 * This handler should be used together with {@link GoBackNArqCodec} and
 * {@link ByteToGoBackNArqDataCodec}.
 * <blockquote>
 * <pre>
 *  {@link ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("arq_codec", <b>new {@link GoBackNArqCodec}()</b>);
 *  p.addLast("arq_handler", <b>new {@link GoBackNArqHandler}(150, Duration.ofMillis(100), Duration.ofMills(100).dividedBy(10))</b>);
 *  p.addLast("buf_codec", <b>new {@link ByteToGoBackNArqDataCodec}()</b>);
 *  ...
 *  p.addLast("handler", new HttpRequestHandler());
 *  </pre>
 * </blockquote>
 */
public class GoBackNArqHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GoBackNArqHandler.class);
    private final int windowSize;
    private Window window;
    private PendingWriteQueue overflow;
    private UnsignedInteger nextInboundSequenceNo;
    private UnsignedInteger base;
    private UnsignedInteger nextSeqNum;
    private final Duration retryTimeout;
    private boolean firstOutbound;
    private ScheduledFuture<?> retryTask;
    private final boolean windowShouldAffectWritability;
    private final Duration ackClock;
    private ScheduledFuture<?> ackTask;
    private boolean ackRequired;

    /**
     * Creates a new GoBackNArqHandler.
     * <p>
     * When specifying the window size, you should take the MTU in bytes, the maximum link capacity
     * (LC) in bytes, and the RTT in ms into account. For example, a good window size could be (LC *
     * RTT) / (MTU * 2000).
     * <p>
     * The retry timeout should be at least twice as large as the time needed to transfer a single
     * frame.
     *
     * @param windowSize                    the window size
     * @param retryTimeout                  the retry timeout
     * @param base                          the first unacknowledged sequence number
     * @param nextSeqNum                    the next sequence number, that was not already send
     * @param nextInboundSequenceNo         the next expected inbound sequence number
     * @param windowShouldAffectWritability if the window should be added to the channels pending
     *                                      bytes
     * @param ackClock                      the frequency of sending ACKs for received packages
     */
    public GoBackNArqHandler(final int windowSize,
                             final Duration retryTimeout,
                             final UnsignedInteger base,
                             final UnsignedInteger nextSeqNum,
                             final UnsignedInteger nextInboundSequenceNo,
                             final boolean windowShouldAffectWritability,
                             final Duration ackClock) {
        this.windowSize = windowSize;
        this.retryTimeout = retryTimeout;
        this.base = base;
        this.nextSeqNum = nextSeqNum;
        this.nextInboundSequenceNo = nextInboundSequenceNo;
        this.firstOutbound = true;
        this.windowShouldAffectWritability = windowShouldAffectWritability;
        this.ackClock = ackClock;
    }

    /**
     * Creates a new GoBackNArqHandler.
     * <p>
     * When specifying the window size, you should take the MTU, the maximum link capacity (LC), and
     * the RTT into account. For example, a good window size could be (LC*RTT) / (1000*MTU).
     * <p>
     * The retry timeout should be at least twice as large as the time needed to transfer a single
     * window.
     *
     * @param windowSize   the window size
     * @param retryTimeout the retry timeout
     * @param ackClock     the frequency of sending ACKs for received packages
     */
    public GoBackNArqHandler(final int windowSize,
                             final Duration retryTimeout,
                             final Duration ackClock) {
        this(windowSize, retryTimeout, UnsignedInteger.MIN_VALUE,
                UnsignedInteger.MIN_VALUE, UnsignedInteger.MIN_VALUE, false, ackClock);
    }

    /**
     * Creates a new GoBackNArqHandler.
     * <p>
     * When specifying the window size, you should take the MTU, the maximum link capacity (LC), and
     * the RTT into account. For example, a good window size could be (LC*RTT) / (1000*MTU).
     * <p>
     * The retry timeout should be at least twice as large as the time needed to transfer a single
     * window.
     *
     * @param windowSize                    the window size
     * @param retryTimeout                  the retry timeout
     * @param windowShouldAffectWritability if the window should be added to the channels pending
     *                                      bytes
     * @param ackClock                      the frequency of sending ACKs for received packages
     */
    public GoBackNArqHandler(final int windowSize,
                             final Duration retryTimeout,
                             final boolean windowShouldAffectWritability,
                             final Duration ackClock) {
        this(windowSize, retryTimeout, UnsignedInteger.MIN_VALUE,
                UnsignedInteger.MIN_VALUE, UnsignedInteger.MIN_VALUE, windowShouldAffectWritability, ackClock);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        //noinspection unchecked
        LOG.trace("[{}] Used windows size of {} and retry timeout of {}ms", ctx.channel()::id, () -> windowSize, retryTimeout::toMillis);
        if (windowShouldAffectWritability) {
            this.window = new PendingQueueWindow(ctx, windowSize);
        }
        else {
            this.window = new SimpleWindow(windowSize);
        }
        this.overflow = new PendingWriteQueue(ctx);

        if (ctx.channel().isActive()) {
            // try to synchronize sequence numbers
            synchronizeSequenceNumbers(ctx);
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        // try to synchronize sequence numbers
        synchronizeSequenceNumbers(ctx);
        ctx.fireChannelActive();
    }

    private void synchronizeSequenceNumbers(final ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new GoBackNArqRst());
        ackTask(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        window.removeAndFailAll(new ClosedChannelException());
        overflow.removeAndFailAll(new ClosedChannelException());
        ctx.fireChannelInactive();
        stopTimer();
        stopAckTask();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        // if we receive this message and our nextInboundSequenceNo > 0, then
        // we have to synchronize/reset our nextInboundSequenceNo
        if (msg instanceof GoBackNArqFirstData) {
            if (nextInboundSequenceNo.getValue() > 0) {
                LOG.trace("[{}] Got first data {}. Reset sequence number.", ctx.channel().id()::asShortText, () -> msg);

                nextInboundSequenceNo = UnsignedInteger.MIN_VALUE;
            }
            // reply with ACK of current message
            ctx.writeAndFlush(new GoBackNArqAck(UnsignedInteger.MIN_VALUE));
        }

        if (msg instanceof AbstractGoBackNArqData) {
            final AbstractGoBackNArqData data = (AbstractGoBackNArqData) msg;

            ackRequired = true;

            if (!data.sequenceNo().equals(nextInboundSequenceNo)) {
                //noinspection unchecked
                LOG.trace("[{}] Got unexpected data {}. Expected {}. Drop it.", ctx.channel().id()::asShortText, () -> data, () -> nextInboundSequenceNo);
                data.release();
            }
            else {
                LOG.trace("[{}] Got expected {}. Pass through.", ctx.channel().id()::asShortText, () -> data);

                // send explicit ACK on last message
                if (msg instanceof GoBackNArqLastData) {
                    // reply with ACK of current message
                    ctx.writeAndFlush(new GoBackNArqAck(nextInboundSequenceNo));
                }

                // increase sequence no
                nextInboundSequenceNo = nextInboundSequenceNo.safeIncrement();

                // expected sequence no -> pass DATA inbound
                ctx.fireChannelRead(msg);
            }
        }
        else if (msg instanceof GoBackNArqAck) {
            final GoBackNArqAck ack = (GoBackNArqAck) msg;
            LOG.trace("[{}] Got {}", ctx.channel().id()::asShortText, () -> ack);

            if (ack.sequenceNo().safeIncrement().equals(nextSeqNum)) {
                stopTimer();
            }
            else {
                resetTimer(ctx);
            }

            // allow also cumulative acknowledgment
            if (ack.sequenceNo().getValue() >= base.getValue() && ack.sequenceNo().getValue() < nextSeqNum.getValue()) {
                final long cumAck = (ack.sequenceNo().getValue() - base.getValue()) + 1;

                base = ack.sequenceNo().safeIncrement();
                succeedWrites(ctx, cumAck);
            }
            else if (base.getValue() > nextSeqNum.getValue()) { // check for overflow
                final long cumAck = (UnsignedInteger.MAX_VALUE.getValue() - base.getValue()) + (ack.sequenceNo().getValue() + 1);

                base = ack.sequenceNo().safeIncrement();
                succeedWrites(ctx, cumAck);
            }
            else {
                // unexpected, just drop, may do not even log this, do to cumulative acknowledgment
                LOG.trace("[{}] Got unexpected (maybe out-of-order) {}. Drop it.", ctx.channel().id()::asShortText, () -> ack);
            }
        }
        else if (msg instanceof GoBackNArqRst) {
            // the receiver was started, maybe our sequence numbers are not synchronized anymore - reset
            if (base.getValue() != 0) {
                base = UnsignedInteger.MIN_VALUE;
                nextSeqNum = UnsignedInteger.MIN_VALUE;
            }
        }
        else {
            // no GBN message -> pass through
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof GoBackNArqData) {
            overflow.add(msg, promise);
            writeData(ctx);
        }
        else {
            // pass through
            ctx.write(msg, promise);
        }
    }

    /**
     * We have to remove {@code cumAck} elements from our {@link #window} and add up to
     * {@code cumAck} elements from {@link #overflow} to {@link #window}.
     *
     * @param ctx    the handler context
     * @param cumAck the amount of acknowledged packages
     */
    private void succeedWrites(final ChannelHandlerContext ctx, final long cumAck) {
        for (long i = 0; i < cumAck; i++) {
            window.remove().trySuccess();
        }

        writeData(ctx);
        ctx.flush();
    }

    /**
     * We will copy data from the overflow to the current window and send the new messages.
     *
     * @param ctx the handler context
     */
    private void writeData(final ChannelHandlerContext ctx) {
        final int freeSpace = Math.min(window.getFreeSpace(), overflow.size());
        for (int i = 0; i < freeSpace; i++) {
            final Object o = overflow.current();
            if (o == null) {
                overflow.remove();
                continue;
            }
            final GoBackNArqData msg = ((GoBackNArqData) o);
            msg.content().retain(); // we must retain, because the remove operation frees the buffer
            final ChannelPromise promise = overflow.remove();

            if (promise.isDone()) {
                ReferenceCountUtil.safeRelease(msg);
                continue;
            }

            window.add(msg, promise); // copy to window

            send(ctx, msg, nextSeqNum);

            if (base.equals(nextSeqNum)) { // reset timer, if first message in window
                resetTimer(ctx);
            }

            nextSeqNum = nextSeqNum.safeIncrement();
        }
    }

    /**
     * We will write the data to the channel.
     *
     * @param ctx   the handler context
     * @param msg   the message to write
     * @param seqNo the sequence number
     */
    private void send(final ChannelHandlerContext ctx,
                      final GoBackNArqData msg,
                      final UnsignedInteger seqNo) {
        if (!ctx.channel().isActive()) {
            window.removeAndFailAll(new ClosedChannelException());
            overflow.removeAndFailAll(new ClosedChannelException());
            stopTimer();
            stopAckTask();
        }
        else {
            final AbstractGoBackNArqData data;
            if (firstOutbound) {
                firstOutbound = false;
                data = new GoBackNArqFirstData(msg.content().retainedSlice());
            }
            else {
                if (overflow.isEmpty()) {
                    data = new GoBackNArqLastData(seqNo, msg.content().retainedSlice());
                }
                else {
                    data = new GoBackNArqData(seqNo, msg.content().retainedSlice());
                }
            }
            LOG.trace("[{}] Write {}", ctx.channel().id()::asShortText, () -> data);
            ctx.write(data);
        }
    }

    /**
     * Resets the retry timer task.
     *
     * @param ctx the handler context
     */
    private void resetTimer(final ChannelHandlerContext ctx) {
        LOG.trace("[{}] Reset timer", ctx.channel().id()::asShortText);
        stopTimer();
        retryTask = ctx.executor().schedule(() -> resend(ctx), retryTimeout.toMillis(), MILLISECONDS);
    }

    /**
     * Stops the retry timer task.
     */
    private void stopTimer() {
        if (retryTask != null) {
            LOG.trace("Reset timer");
            retryTask.cancel(true);
            retryTask = null;
        }
    }

    /**
     * Resends the complete window on timeout.
     *
     * @param ctx the handler context
     */
    private void resend(final ChannelHandlerContext ctx) {
        UnsignedInteger seqNo = UnsignedInteger.of(base.getValue());

        // resend all messages from window
        if (window.size() != 0) {
            LOG.info("[{}] ACKs got timeout. Resend complete window of size {}", ctx.channel().id()::asShortText, window::size);
            for (final Window.Frame frame : window.getQueue()) {
                if (frame.getPromise().isDone()) {
                    window.remove();
                    continue;
                }

                send(ctx, frame.getMsg(), seqNo);
                seqNo = seqNo.safeIncrement();
            }

            ctx.flush();

            resetTimer(ctx);
        }
    }

    /**
     * Acknowledge received packages. We do this as task to avoid congestion and save bandwidth.
     */
    private void ackTask(final ChannelHandlerContext ctx) {
        if (ackRequired) {
            ackRequired = false;
            // reply with ACK of current inbound index
            ctx.writeAndFlush(new GoBackNArqAck(nextInboundSequenceNo.safeDecrement()));
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
