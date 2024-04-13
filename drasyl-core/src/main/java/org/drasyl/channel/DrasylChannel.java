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
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Objects.requireNonNull;

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
    private final Queue<Object> inboundBuffer = PlatformDependent.newSpscQueue();
    private final Runnable readTask = () -> {
        // ensure the inboundBuffer is not empty as readInbound() will always call fireChannelReadComplete()
        if (!inboundBuffer.isEmpty()) {
            readInbound();
        }
    };
    private volatile State state;
    private volatile Identity identity;
    private final DrasylAddress remoteAddress;
    private final PeersManager peersManager;
    private final DatagramChannel udpChannel;
    private volatile boolean readInProgress;
    private volatile boolean writeInProgress;
    private volatile Future<?> finishReadFuture;

    @UnstableApi
    DrasylChannel(final Channel parent,
                  final State state,
                  final Identity identity,
                  final DrasylAddress remoteAddress,
                  final PeersManager peersManager,
                  final DatagramChannel udpChannel) {
        super(parent);
        this.state = state;
        this.identity = requireNonNull(identity);
        this.remoteAddress = remoteAddress;
        this.peersManager = requireNonNull(peersManager);
        this.udpChannel = requireNonNull(udpChannel);
    }

    DrasylChannel(final DrasylServerChannel parent,
                  final DrasylAddress remoteAddress,
                  final PeersManager peersManager,
                  final DatagramChannel udpChannel) {
        this(parent, null, parent.identity(), remoteAddress, peersManager, udpChannel);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new DrasylChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    public Identity identity() {
        return identity;
    }

    @Override
    protected SocketAddress localAddress0() {
        return identity.getAddress();
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

        identity = null;

        state = State.CLOSED;

        releaseInboundBuffer();
    }

    void readInbound() {
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

        // all messages read, fire channelReadComplete event
        pipeline.fireChannelReadComplete();
    }

    @Override
    protected void doBeginRead() {
        if (readInProgress) {
            return;
        }

        if (inboundBuffer.isEmpty()) {
            // set to true to make sure this read operation will be performed when something is
            // written to the inbound buffer
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
                LOG.warn("Closing DrasylChannel {} because exception occurred!", this, cause);
                close();
                PlatformDependent.throwException(cause);
            }
        }
    }

    /**
     * This method places the message {@code o} in the queue for inbound messages to be read by
     * this channel. Queued messages are not processed until {@link #finishRead()} is called.
     */
    public void queueRead(final Object o) {
        inboundBuffer.add(o);
    }

    /**
     * This method start processing (if any) queued inbound messages. This method ensures that
     * read/write order is respected. Therefore, if channel is currently writing, these writes are
     * performed first and the reads are performed afterwards.
     */
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
                finishReadFuture = eventLoop().submit(this::finishRead0);
            }
            else {
                eventLoop().execute(this::finishRead0);
            }
        }
        catch (final Throwable cause) {
            LOG.warn("Closing DrasylChannel {} because execption occurred!", this, cause);
            close();
            PlatformDependent.throwException(cause);
        }
    }

    @Override
    public DrasylServerChannel parent() {
        return (DrasylServerChannel) super.parent();
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
            while (true) {
                final ByteBuf buf = (ByteBuf) in.current();
                if (buf == null) {
                    break;
                }

                // map remoteAddress to udp endpoint
                peersManager.applicationMessageSentOrReceived(remoteAddress);
                InetSocketAddress endpoint = peersManager.getEndpoint(remoteAddress);
                if (endpoint == null) {
                    endpoint = peersManager.getEndpoint(peersManager.getDefaultPeer());
                }
                if (endpoint != null) {
                    // convert to remote message
                    final int networkId = parent().config().getNetworkId();
                    final ApplicationMessage appMsg = ApplicationMessage.of(networkId, (IdentityPublicKey) remoteAddress, identity.getIdentityPublicKey(), identity.getProofOfWork(), buf.retain());
                    final InetAddressedMessage<ApplicationMessage> inetMsg = new InetAddressedMessage<>(appMsg, endpoint);

                    parent().queueUdpWrite(inetMsg);
                }
                else {
                    LOG.warn("Discard messages as no path exist to peer `{}`.", remoteAddress);
                }

                in.remove();
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

    private void releaseInboundBuffer() {
        readInProgress = false;
        Object msg;
        while ((msg = inboundBuffer.poll()) != null) {
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
