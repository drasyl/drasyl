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
package org.drasyl.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A virtual {@link Channel} for peer communication.
 * <p>
 * (Currently) only compatible with {@link io.netty.channel.nio.NioEventLoop}.
 * <p>
 * Inspired by {@link io.netty.channel.local.LocalChannel}.
 *
 * @see DrasylServerChannel
 */
@UnstableApi
public class DrasylChannel extends AbstractChannel {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylChannel.class);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ')';
    private static final int MAX_READER_STACK_DEPTH = 8;
    private static final AtomicReferenceFieldUpdater<DrasylChannel, Future> FINISH_READ_FUTURE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DrasylChannel.class, Future.class, "finishReadFuture");

    enum State {OPEN, CONNECTED, CLOSED}

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelConfig config = new DefaultChannelConfig(this);
    final Queue<Object> inboundBuffer = PlatformDependent.newSpscQueue();
    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            // ensure the inboundBuffer is not empty as readInbound() will always call fireChannelReadComplete()
            if (!inboundBuffer.isEmpty()) {
                readInbound();
            }
        }
    };
    private final Runnable finishReadTask = new Runnable() {
        @Override
        public void run() {
            finishRead0();
        }
    };
    private volatile State state;
    volatile boolean pendingWrites;
    private volatile DrasylAddress localAddress; // NOSONAR
    private final DrasylAddress remoteAddress;
    private volatile boolean readInProgress;
    private volatile boolean writeInProgress;
    private volatile Future<?> finishReadFuture;

    @UnstableApi
    DrasylChannel(final Channel parent,
                  final State state,
                  final DrasylAddress localAddress,
                  final DrasylAddress remoteAddress) {
        super(parent);
        this.state = state;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    DrasylChannel(final DrasylServerChannel parent, final DrasylAddress remoteAddress) {
        this(parent, null, parent.localAddress0(), remoteAddress);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DrasylChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    @Override
    protected void doRegister() {
        state = State.CONNECTED;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) {
        throw new AlreadyConnectedException();
    }

    @Override
    protected void doDisconnect() {
        doClose();
    }

    @Override
    protected void doClose() {
        assert eventLoop() == null || eventLoop().inEventLoop();

        localAddress = null;

        state = State.CLOSED;

        releaseInboundBuffers();
    }

    void readInbound() {
        assert eventLoop().inEventLoop();
        final RecvByteBufAllocator.Handle handle = unsafe().recvBufAllocHandle();
        handle.reset(config());
        final ChannelPipeline pipeline = pipeline();
        do {
            final Object received = inboundBuffer.poll();
            if (received == null) {
                break;
            }
            handle.incMessagesRead(1);
            pipeline.fireChannelRead(received);
        } while (handle.continueReading());
        handle.readComplete();
        pipeline.fireChannelReadComplete();
    }

    @Override
    protected void doBeginRead() {
        if (readInProgress) {
            return;
        }

        if (inboundBuffer.isEmpty()) {
            readInProgress = true;
            return;
        }

        // check stack depth; this is relevant when multiple channels with the same event loop are
        // heavily interacting with each other and, therefore pass messages to each other in one run
        final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
        final int stackDepth = threadLocals.localChannelReaderStackDepth();
        if (stackDepth < MAX_READER_STACK_DEPTH) {
            threadLocals.setLocalChannelReaderStackDepth(stackDepth + 1);
            try {
                readInbound();
            }
            finally {
                threadLocals.setLocalChannelReaderStackDepth(stackDepth);
            }
        }
        else {
            try {
                eventLoop().execute(readTask);
            }
            catch (final Throwable cause) {
                LOG.warn("Closing DrasylChannel {} because execption occurred!", this, cause);
                close();
                PlatformDependent.throwException(cause);
            }
        }
    }

    public void finishRead() {
        // check whether the channel is currently writing; if so, we must schedule the event in the
        // event loop to maintain the read/write order.
        if (eventLoop().inEventLoop() && !writeInProgress) {
            finishRead0();
        }
        else {
            runFinishReadTask();
        }
    }

    private void finishRead0() {
        final Future<?> thisFinishReadFuture = this.finishReadFuture;
        if (thisFinishReadFuture != null) {
            if (!thisFinishReadFuture.isDone()) {
                runFinishReadTask();
                return;
            }
            else {
                // lazy unset to make sure we don't prematurely unset it while scheduling a new task.
                FINISH_READ_FUTURE_UPDATER.compareAndSet(this, thisFinishReadFuture, null);
            }
        }
        // we should only set readInProgress to false if there is any data that was read as
        // otherwise we may miss to forward data later on.
        if (readInProgress && !inboundBuffer.isEmpty()) {
            readInProgress = false;
            readInbound();
        }
    }

    private void runFinishReadTask() {
        try {
            if (writeInProgress) {
                finishReadFuture = eventLoop().submit(finishReadTask);
            }
            else {
                eventLoop().execute(finishReadTask);
            }
        }
        catch (final Throwable cause) {
            LOG.warn("Closing DrasylChannel {} because execption occurred!", this, cause);
            close();
            PlatformDependent.throwException(cause);
        }
    }

    @SuppressWarnings("java:S135")
    @Override
    protected void doWrite(final ChannelOutboundBuffer in) throws Exception {
        switch (state) {
            case OPEN:
                throw new NotYetConnectedException();
            case CLOSED:
                throw new ClosedChannelException();
            case CONNECTED:
                break;
        }

        writeInProgress = true;
        try {
            // if we write directly to another DrasylChannel in the future, we will have to take
            // another look at the LocalChannel implementation and how a closed target channel is
            // handled there. This is currently irrelevant to us, as we only write to the
            // DrasylServerChannel, which is always the last to be closed.
            boolean wroteToParent = false;
            pendingWrites = false;
            while (true) {
                final Object msg = in.current();
                if (msg == null) {
                    break;
                }

                if (!parent().isWritable()) {
                    pendingWrites = true;
                    break;
                }

                ReferenceCountUtil.retain(msg);
                parent().write(new OverlayAddressedMessage<>(msg, remoteAddress, localAddress)).addListener(future -> {
                    if (!future.isSuccess()) {
                        LOG.warn("Outbound message `{}` written from channel `{}` to server channel failed:", () -> msg, () -> this, future::cause);
                    }
                });
                in.remove();
                wroteToParent = true;
            }

            if (wroteToParent) {
                // only pass flush event to parent channel if we actually have wrote something
                parent().flush();
            }
        }
        finally {
            writeInProgress = false;
        }
    }

    @Override
    protected Object filterOutboundMessage(final Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            return super.filterOutboundMessage(msg);
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    private void releaseInboundBuffers() {
        assert eventLoop() == null || eventLoop().inEventLoop();
        readInProgress = false;
        Object msg;
        while ((msg = this.inboundBuffer.poll()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.CONNECTED;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    /**
     * Returns {@code true} if remote peer is reachable via a direct path.
     *
     * @return {@code true} if remote peer is reachable via a direct path.
     */
    public boolean isDirectPathPresent() {
        return ((DrasylServerChannel) parent()).paths.get(remoteAddress) != null;
    }

    private class DrasylChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress,
                            final SocketAddress localAddress,
                            final ChannelPromise promise) {
            throw new AlreadyConnectedException();
        }
    }
}
