package org.drasyl.handler.connection;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayDeque;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.SerialNumberArithmetic.lessThanOrEqualTo;

// es kann sein, dass wir in einem Rutsch (durch mehrere channelReads) Segmente empfangen und die dann z.B. alle jeweils ACKen
// zum Zeitpunkt des channelReads wissen wir noch nicht, ob noch mehr kommt
// daher speichern wir die nachrichten und warten auf ein channelReadComplete. Dort gucken wir dann, ob wir z.B. ACKs zusammenfassen können/etc.
class OutgoingSegmentQueue {
    private static final Logger LOG = LoggerFactory.getLogger(OutgoingSegmentQueue.class);
    private final ChannelHandlerContext ctx;
    private final ConnectionHandshakeHandler handler;
    private final ArrayDeque<OutgoingSegmentEntry> deque;

    OutgoingSegmentQueue(final ChannelHandlerContext ctx,
                         final ConnectionHandshakeHandler handler,
                         final ArrayDeque<OutgoingSegmentEntry> deque) {
        this.ctx = requireNonNull(ctx);
        this.handler = requireNonNull(handler);
        this.deque = requireNonNull(deque);
    }

    OutgoingSegmentQueue(final ChannelHandlerContext ctx,
                         final ConnectionHandshakeHandler handler) {
        this(ctx, handler, new ArrayDeque<>());
    }

    public ChannelPromise add(final ConnectionHandshakeSegment seg,
                              final ChannelPromise writePromise,
                              final ChannelPromise ackPromise) {
        deque.add(new OutgoingSegmentEntry(seg, writePromise, ackPromise));
        return writePromise;
    }

    public ChannelPromise add(final ConnectionHandshakeSegment seg,
                              final ChannelPromise writePromise) {
        deque.add(new OutgoingSegmentEntry(seg, writePromise, ctx.newPromise()));
        return writePromise;
    }

    public ChannelPromise add(final ConnectionHandshakeSegment seg) {
        return add(seg, ctx.newPromise());
    }

    public ChannelPromise addAndFlush(final ConnectionHandshakeSegment seg,
                                      final ChannelPromise writePromise,
                                      final ChannelPromise ackPromise) {
        try {
            return add(seg, writePromise, ackPromise);
        }
        finally {
            flush();
        }
    }

    public ChannelPromise addAndFlush(final ConnectionHandshakeSegment seg,
                                      final ChannelPromise writePromise) {
        return addAndFlush(seg, writePromise, ctx.newPromise());
    }

    public ChannelPromise addAndFlush(final ConnectionHandshakeSegment seg) {
        return addAndFlush(seg, ctx.newPromise());
    }

    public void flush() {
        if (deque.isEmpty()) {
            return;
        }

        final int size = deque.size();
        LOG.trace("{}[{}] Channel read complete. Now check if we can repackage/cumulate {} outgoing segments.", ctx.channel(), handler.state, size);
        if (size == 1) {
            final OutgoingSegmentEntry current = deque.remove();
            LOG.trace("{}[{}] Outgoing queue 1: Write and flush `{}`.", ctx.channel(), handler.state, current.seg());
            write(current);
            ctx.flush();
            return;
        }

        // multiple segments in queue. Check if we can cumulate them
        OutgoingSegmentEntry current = deque.poll();
        while (!deque.isEmpty()) {
            OutgoingSegmentEntry next = deque.remove();

            if (current.seg().isOnlyAck() && next.seg().isOnlyAck() && current.seg().seq() == next.seg().seq() && lessThanOrEqualTo(current.seg().seq(), next.seg().seq(), ConnectionHandshakeHandler.SEQ_NO_SPACE)) {
                // cumulate ACKs
                LOG.trace("{}[{}] Outgoing queue: Current segment `{}` is followed by segment `{}` with same flags set, same SEQ, and >= ACK. We can purge current segment.", ctx.channel(), handler.state, current.seg(), next.seg());
                current.seg().release();
                next.writePromise().addListener(new PromiseNotifier<>(current.writePromise()));
                next.ackPromise().addListener(new PromiseNotifier<>(current.ackPromise()));
            }
            else if (current.seg().isOnlyAck() && current.seg().seq() == next.seg().seq() && current.seg().len() == 0) {
                // piggyback ACK
                LOG.trace("{}[{}] Outgoing queue: Piggyback current ACKnowledgement `{}` to next segment `{}`.", ctx.channel(), handler.state, current.seg(), next.seg());
                next = new OutgoingSegmentEntry(ConnectionHandshakeSegment.piggybackAck(next.seg(), current.seg()), next.writePromise(), next.ackPromise());
                next.writePromise().addListener(new PromiseNotifier<>(current.writePromise()));
                next.ackPromise().addListener(new PromiseNotifier<>(current.ackPromise()));
            }
            else {
                LOG.trace("{}[{}] Outgoing queue 2: Write `{}`.", ctx.channel(), handler.state, current.seg());
                write(current);
            }

            current = next;
        }

        LOG.trace("{}[{}] Outgoing queue 3: Write and flush `{}`.", ctx.channel(), handler.state, current.seg());
        write(current);
        ctx.flush();
    }

    private void write(final OutgoingSegmentEntry entry) {
        final boolean mustBeAcked = mustBeAcked(entry.seg());
        if (mustBeAcked) {
            handler.retransmissionQueue().add(entry.seg(), entry.ackPromise());
            entry.writePromise().addListener(new RetransmissionTimeoutApplier(ctx, entry.seg(), entry.ackPromise(), handler));
            final ConnectionHandshakeSegment copy = entry.seg().copy();

            // RTTM
            copy.setTsVal(System.nanoTime() / 1_000_000);
            copy.setTsEcr(handler.tcb().tsRecent);
            handler.tcb().lastAckSent = entry.seg().ack();

            ctx.write(copy, entry.writePromise()).addListener(CLOSE_ON_FAILURE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                    LOG.trace("{}[{}] WRITTEN `{}`: {}", ctx.channel(), handler.state, entry.seg(), channelFuture.isSuccess());
                }
            });
        }
        else {
            // RTTM
            entry.seg().setTsVal(System.nanoTime() / 1_000_000);
            entry.seg().setTsEcr(handler.tcb().tsRecent);
            handler.tcb().lastAckSent = entry.seg().ack();

            entry.writePromise().addListener(new PromiseNotifier<>(entry.ackPromise()));
            ctx.write(entry.seg(), entry.writePromise()).addListener(CLOSE_ON_FAILURE).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                    LOG.trace("{}[{}] WRITTEN `{}`: {}", ctx.channel(), handler.state, entry.seg(), channelFuture.isSuccess());
                }
            });
        }
    }

    private boolean mustBeAcked(final ConnectionHandshakeSegment seg) {
        return (!seg.isOnlyAck() && !seg.isRst()) || seg.len() != 0;
    }

    public void releaseAndFailAll(final Throwable cause) {
        OutgoingSegmentEntry entry;
        while ((entry = deque.poll()) != null) {
            final ConnectionHandshakeSegment seg = entry.seg();
            final ChannelPromise writePromise = entry.writePromise();
            final ChannelPromise ackPromise = entry.ackPromise();

            seg.release();
            writePromise.tryFailure(cause);
            ackPromise.tryFailure(cause);
        }
    }

    static class OutgoingSegmentEntry {
        private final ConnectionHandshakeSegment seg;
        private final ChannelPromise writePromise;
        private final ChannelPromise ackPromise;

        OutgoingSegmentEntry(final ConnectionHandshakeSegment seg,
                             final ChannelPromise writePromise,
                             final ChannelPromise ackPromise) {
            this.seg = requireNonNull(seg);
            this.writePromise = requireNonNull(writePromise);
            this.ackPromise = requireNonNull(ackPromise);
        }

        @Override
        public String toString() {
            return "OutgoingSegmentEntry{" +
                    "seg=" + seg +
                    ", writePromise=" + writePromise +
                    ", ackPromise=" + ackPromise +
                    '}';
        }

        public ConnectionHandshakeSegment seg() {
            return seg;
        }

        public ChannelPromise writePromise() {
            return writePromise;
        }

        public ChannelPromise ackPromise() {
            return ackPromise;
        }
    }
}
