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
package org.drasyl.channel.arq.stopandwait;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.IdentityHashMap;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.channel.arq.stopandwait.StopAndWaitArqAck.STOP_AND_WAIT_ACK_0;
import static org.drasyl.channel.arq.stopandwait.StopAndWaitArqAck.STOP_AND_WAIT_ACK_1;
import static org.drasyl.util.Preconditions.requirePositive;

/**
 * Performs the Stop-and-wait ARQ protocol.
 * <p>
 * It also updates the {@linkplain Channel#isWritable() writability} of the associated {@link
 * Channel}, so that the pending write operations are also considered to determine the writability.
 * <p>
 * This handler changes the behavior of the {@link io.netty.util.concurrent.Promise}s returned by
 * {@link io.netty.channel.ChannelHandlerContext#write(Object)}: The promise is not complemented
 * until the message's recipient has acknowledged arrival of the message. If you abort the promise,
 * this handler will discard the message.
 * <p>
 * Keep in mind that this ARQ protocol is originally intended to be used in connection-oriented
 * communication. Therefore, it may happen (e.g. due to a restart of one of the two peers) that the
 * expected sequence number of the next message is no longer synchronized. This leads to the loss of
 * the first message. To avoid this, a NOOP message should be sent once at the beginning of the
 * communication.
 * <p>
 * This handler should be used together with {@link ByteToStopAndWaitArqDataCodec} and {@link
 * StopAndWaitArqCodec}.
 * <blockquote>
 * <pre>
 *  {@link ChannelPipeline} p = ...;
 *  ...
 *  p.addLast("arq_codec", <b>new {@link StopAndWaitArqCodec}()</b>);
 *  p.addLast("arq_handler", <b>new {@link StopAndWaitArqHandler}(500, 100)</b>);
 *  p.addLast("buf_codec", <b>new {@link ByteToStopAndWaitArqDataCodec}()</b>);
 *  ...
 *  p.addLast("handler", new HttpRequestHandler());
 *  </pre>
 * </blockquote>
 */
public class StopAndWaitArqHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(StopAndWaitArqHandler.class);
    private PendingWriteQueue pendingWrites;
    private final Map<Object, ChannelPromise> promises = new IdentityHashMap<>();
    private int retryTimeout;
    private boolean expectedInboundSequenceNo;
    private long lastWriteAttempt;

    /**
     * @param retryTimeout              see {@link #setRetryTimeout(int)}
     * @param expectedInboundSequenceNo see {@link #setExpectedInboundSequenceNo(boolean)}
     */
    public StopAndWaitArqHandler(final int retryTimeout, final boolean expectedInboundSequenceNo) {
        setRetryTimeout(retryTimeout);
        setExpectedInboundSequenceNo(expectedInboundSequenceNo);
    }

    /**
     * @param retryTimeout see {@link #setRetryTimeout(int)}
     */
    public StopAndWaitArqHandler(final int retryTimeout) {
        this(retryTimeout, false);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.pendingWrites = new PendingWriteQueue(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        discardPendingWrites(new ClosedChannelException());

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx,
                            final Object msg) {
        if (msg instanceof StopAndWaitArqData) {
            final StopAndWaitArqData data = (StopAndWaitArqData) msg;
            if (expectedInboundSequenceNo == data.sequenceNo()) {
                LOG.trace("[{}] Got expected {}. Pass through DATA.", ctx.channel().id()::asShortText, () -> data);

                // expected sequence no -> pass DATA inbound
                ctx.fireChannelRead(msg);

                // flip sequence no
                expectedInboundSequenceNo = !expectedInboundSequenceNo;
            }
            else {
                LOG.trace("[{}] Got unexpected {}. Drop DATA.", ctx.channel().id()::asShortText, () -> data);
                data.release();
            }

            // reply with ACK
            writeAck(ctx);
        }
        else if (msg instanceof StopAndWaitArqAck) {
            final StopAndWaitArqAck ack = (StopAndWaitArqAck) msg;

            final Boolean outboundSequenceNo = outboundSequenceNo();
            if (outboundSequenceNo != null && outboundSequenceNo != ack.sequenceNo()) {
                LOG.trace("[{}] Got expected {}. Succeed DATA.", ctx.channel().id()::asShortText, () -> ack);
                succeedCurrentWrite(ctx);
                writeNextPending(ctx);
            }
            else {
                LOG.trace("[{}] Got unexpected {}. Ignore.", ctx.channel().id()::asShortText, () -> ack);
            }
        }
        else {
            // no stop and wait message -> pass through
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof StopAndWaitArqData) {
            final StopAndWaitArqData data = (StopAndWaitArqData) msg;
            promises.put(data, promise);
            pendingWrites.add(data, promise);
            promise.addListener(future -> promises.remove(data));
        }
        else {
            // pass through
            ctx.write(msg);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        writeNextPending(ctx);

        ctx.flush();
    }

    /**
     * Defines how long to wait for a {@link StopAndWaitArqAck} message before resending it. {@link
     * StopAndWaitArqData} message is sent again. This timeout should not be smaller than the round
     * trip time of the connection.
     *
     * @param retryTimeout time in ms before the {@link StopAndWaitArqData} message is sent again
     */
    public void setRetryTimeout(final int retryTimeout) {
        this.retryTimeout = requirePositive(retryTimeout);
    }

    /**
     * Sets the sequence of the next inbound {@link StopAndWaitArqData} message. This method can be
     * used to manually synchronize the sequence number of the sender and receiver.
     *
     * @param expectedInboundSequenceNo expected sequence number of the next inbound {@link
     *                                  StopAndWaitArqData} message
     */
    public void setExpectedInboundSequenceNo(final boolean expectedInboundSequenceNo) {
        this.expectedInboundSequenceNo = expectedInboundSequenceNo;
    }

    @SuppressWarnings("java:S2447")
    private Boolean outboundSequenceNo() {
        final StopAndWaitArqData currentWrite = (StopAndWaitArqData) pendingWrites.current();

        if (currentWrite == null) {
            return null;
        }
        else {
            return currentWrite.sequenceNo();
        }
    }

    @SuppressWarnings("java:S135")
    private void writeNextPending(final ChannelHandlerContext ctx) {
        final Channel channel = ctx.channel();
        if (!channel.isActive()) {
            discardPendingWrites(new ClosedChannelException());
            return;
        }

        while (true) {
            final StopAndWaitArqData currentWrite = (StopAndWaitArqData) pendingWrites.current();

            if (currentWrite == null) {
                // no pending write. stop
                break;
            }

            final ChannelPromise promise = promises.get(currentWrite);
            if (promise == null || promise.isDone()) {
                // write is done. go to next write
                pendingWrites.remove();
                continue;
            }

            final long currentTime = System.currentTimeMillis();
            if (currentTime < lastWriteAttempt + retryTimeout) {
                // ACK timeout not reached. do nothing
                break;
            }
            lastWriteAttempt = currentTime;

            // perform next write
            LOG.trace("[{}] Write {}", ctx.channel().id()::asShortText, () -> currentWrite);
            ctx.writeAndFlush(currentWrite.retain()).addListener(future -> {
                if (!future.isSuccess()) {
                    //noinspection unchecked
                    LOG.warn("[{}] Unable to write {}: {}", ctx.channel().id()::asShortText, () -> currentWrite, future::cause);
                }
            });

            // schedule next write operation
            ctx.executor().schedule(() -> writeNextPending(ctx), retryTimeout, MILLISECONDS);

            break;
        }
    }

    private void succeedCurrentWrite(final ChannelHandlerContext ctx) {
        final StopAndWaitArqData currentWrite = (StopAndWaitArqData) pendingWrites.current();

        if (currentWrite != null) {
            LOG.trace("[{}] Succeed {}.", ctx.channel().id()::asShortText, () -> currentWrite);
            pendingWrites.remove().trySuccess();
        }

        // reset timer
        lastWriteAttempt = 0;
    }

    private void discardPendingWrites(final Throwable cause) {
        LOG.trace("[{}] Discard all pending writes ({}).", pendingWrites::size);
        pendingWrites.removeAndFailAll(cause);
    }

    private void writeAck(final ChannelHandlerContext ctx) {
        final StopAndWaitArqAck ack = expectedInboundSequenceNo ? STOP_AND_WAIT_ACK_1 : STOP_AND_WAIT_ACK_0;
        LOG.trace("[{}] Write {}", ctx.channel().id()::asShortText, () -> ack);
        ctx.writeAndFlush(ack).addListener(future -> {
            if (!future.isSuccess()) {
                //noinspection unchecked
                LOG.warn("[{}] Unable to send {}: {}", ctx.channel().id()::asShortText, () -> ack, future::cause);
            }
        });
    }
}
