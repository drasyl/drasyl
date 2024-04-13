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

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.internal.PlatformDependent;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Objects.requireNonNull;

/**
 * A virtual {@link io.netty.channel.ServerChannel} used for overlay network management. This
 * channel must be bind to an {@link Identity}.
 * <p>
 * (Currently) only compatible with {@link io.netty.channel.nio.NioEventLoop}.
 * <p>
 * Inspired by {@link io.netty.channel.local.LocalServerChannel}.
 *
 * @see DrasylChannel
 */
@UnstableApi
public class DrasylServerChannel extends AbstractServerChannel {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylServerChannel.class);
    private static final AtomicReferenceFieldUpdater<DrasylServerChannel, Future> FINISH_UDP_WRITE_FUTURE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DrasylServerChannel.class, Future.class, "finishUdpWriteFuture");
    public DatagramChannel udpChannel;

    enum State {OPEN, ACTIVE, CLOSED}

    private volatile State state;
    private final DrasylServerChannelConfig config = new DrasylServerChannelConfig(this);
    public final Map<SocketAddress, DrasylChannel> channels;
    private volatile Identity identity; // NOSONAR
    final SetMultimap<DrasylAddress, Object> paths = new HashSetMultimap<>();

    private final Queue<Object> outboundUdpBuffer = PlatformDependent.newMpscQueue();
    private volatile boolean udpReadInProgress;
    private volatile Future<?> finishUdpWriteFuture;

    @SuppressWarnings("java:S2384")
    DrasylServerChannel(final State state,
                        final Map<SocketAddress, DrasylChannel> channels,
                        final Identity identity) {
        this.state = requireNonNull(state);
        this.channels = requireNonNull(channels);
        this.identity = identity;
    }

    @SuppressWarnings("unused")
    public DrasylServerChannel() {
        this(State.OPEN, new ConcurrentHashMap<>(), null);
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    public Identity identity() {
        return identity;
    }

    @Override
    protected DrasylAddress localAddress0() {
        if (identity != null) {
            return identity.getAddress();
        }
        else {
            return null;
        }
    }

    @Override
    protected void doBind(final SocketAddress identity) {
        if (!(identity instanceof Identity)) {
            throw new IllegalArgumentException("Unsupported address type! Expected `" + Identity.class.getSimpleName() + "`, but got `" + identity.getClass().getSimpleName() + "`.");
        }

        this.identity = (Identity) identity;
        state = State.ACTIVE;
    }

    @Override
    protected void doRegister() throws Exception {
        super.doRegister();

        pipeline().addLast(new ChannelInitializer<>() {
            @Override
            public void initChannel(final Channel ch) {
                ch.pipeline().addLast(new ChildChannelRouter(paths));
                ch.pipeline().addLast(new DuplicateChannelFilter());
            }
        });
    }

    @Override
    protected void doClose() {
        if (state != State.CLOSED) {
            // Update the internal state before the closeFuture<?> is notified.
            if (identity != null) {
                identity = null;
            }
            state = State.CLOSED;
        }

        releaseOutboundBuffer();
    }

    private void releaseOutboundBuffer() {
        Object msg;
        while ((msg = outboundUdpBuffer.poll()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    protected void doBeginRead() {
        // NOOP
        // all inbound messages (UdpServer, UdpMulticastServer, TcpServer, TcpClient, ..) are
        // currently automatically pushed to us. no need to pull reads
    }

    @Override
    public DrasylServerChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    protected DrasylChannel newDrasylChannel(final DrasylAddress peer) {
        return new DrasylChannel(this, peer, config().getPeersManager(), udpChannel);
    }

    public Promise<DrasylChannel> serve(final DrasylAddress peer, final Promise<DrasylChannel> promise) {
        if (eventLoop().inEventLoop()) {
            promise.trySuccess(serve0(peer));
        }
        else {
            eventLoop().execute(() -> promise.trySuccess(serve0(peer)));
        }

        return promise;
    }

    private DrasylChannel serve0(final DrasylAddress peer) {
        assert eventLoop().inEventLoop();
        DrasylChannel channel = channels.get(peer);
        if (channel == null) {
            channel = newDrasylChannel(peer);
            pipeline().fireChannelRead(channel);
            pipeline().fireChannelReadComplete();
        }
        return channel;
    }

    public Promise<DrasylChannel> serve(final DrasylAddress peer) {
        return serve(peer, new DefaultPromise<>(eventLoop()));
    }

    /**
     * This method places the message {@code o} in the queue for outbound messages to be written by
     * the UDP channel. Queued message are not processed until {@link #finishUdpWrite()} is called.
     */
    public void queueUdpWrite(final Object o) {
        outboundUdpBuffer.add(o);
    }

    /**
     * This method start processing (if any) queued outbound messages for the UDP channel. This
     * method ensures that read/write order is respected. Therefore, if UDP channel is currently
     * reading, these reads are performed first and the writes are performed afterwards.
     */
    public void finishUdpWrite() {
        // check whether the channel is currently reading; if so, we must schedule the event in the
        // event loop to maintain the read/write order.
        if (udpChannel.eventLoop().inEventLoop() && !udpReadInProgress) {
            finishUdpWrite0();
        }
        else {
            runFinishUdpWriteTask();
        }
    }

    private void finishUdpWrite0() {
        final Future<?> thisFinishUdpWriteFuture = this.finishUdpWriteFuture;
        if (thisFinishUdpWriteFuture != null) {
            if (!thisFinishUdpWriteFuture.isDone()) {
                runFinishUdpWriteTask();
                return;
            }
            else {
                // lazy unset to make sure we don't prematurely unset it while scheduling a new task.
                FINISH_UDP_WRITE_FUTURE_UPDATER.compareAndSet(this, thisFinishUdpWriteFuture, null);
            }
        }
        // we should only set readInProgress to false if there is any data that was read as
        // otherwise we may miss to forward data later on.
        if (!outboundUdpBuffer.isEmpty()) {
            writeUdpChannel();
        }
    }

    private void runFinishUdpWriteTask() {
        try {
            if (udpReadInProgress) {
                finishUdpWriteFuture = udpChannel.eventLoop().submit(this::finishUdpWrite0);
            }
            else {
                udpChannel.eventLoop().execute(this::finishUdpWrite0);
            }
        }
        catch (final Throwable cause) {
            LOG.warn("Closing DrasylServerChannel {} because exception occurred!", this, cause);
            close();
            PlatformDependent.throwException(cause);
        }
    }

    void writeUdpChannel() {
        do {
            final Object o = outboundUdpBuffer.poll();
            if (o == null) {
                break;
            }
            udpChannel.write(o).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Outbound message `{}` written to UDP channel failed:", () -> o, future::cause);
                }
            });
        } while (true); // TODO: use isWritable?

        // all messages written, fire flush event
        udpChannel.flush();
    }

    /**
     * This handler routes inbound messages and events to the correct child channel. If there is
     * currently no child channel, a new one is automatically created.
     */
    private static class ChildChannelRouter extends ChannelDuplexHandler {
        private final SetMultimap<DrasylAddress, Object> paths;

        @SuppressWarnings("java:S2384")
        ChildChannelRouter(final SetMultimap<DrasylAddress, Object> paths) {
            this.paths = requireNonNull(paths);
        }

        @Override
        public void close(final ChannelHandlerContext ctx,
                          final ChannelPromise promise) throws Exception {
            // close all child channels first...
            final PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
            for (final Channel channel : ((DrasylServerChannel) ctx.channel()).channels.values()) {
                combiner.add(channel.close());
            }
            final Promise<Void> aggregatePromise = ctx.newPromise();
            combiner.finish(aggregatePromise);

            // ...then pass close event further through the pipeline
            aggregatePromise.addListener(future -> {
                if (future.isSuccess()) {
                    // all child channels has been successfully closed
                    ctx.close(promise);
                }
                else {
                    // there was an error, close channel and return error then.
                    ctx.close().addListener(future2 -> promise.tryFailure(future.cause()));
                }
            });
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx,
                                       final Object evt) {
            if (evt instanceof PathEvent) {
                try {
                    final PathEvent pathEvent = (PathEvent) evt;
                    final DrasylAddress peer = pathEvent.getAddress();

                    if (pathEvent instanceof AddPathEvent || pathEvent instanceof AddPathAndSuperPeerEvent || pathEvent instanceof AddPathAndChildrenEvent) {
                        addPath(ctx, peer, pathEvent.getPath());
                    }
                    else if (pathEvent instanceof RemovePathEvent || pathEvent instanceof RemoveSuperPeerAndPathEvent || pathEvent instanceof RemoveChildrenAndPathEvent) {
                        removePath(ctx, peer, pathEvent.getPath());
                    }
                }
                catch (final ClassCastException e) {
                    LOG.debug("Can't cast address of event `{}`: ", evt, e);
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        private void addPath(final ChannelHandlerContext ctx,
                             final DrasylAddress address,
                             final Object path) {
            requireNonNull(address);
            requireNonNull(path);

            final boolean firstPath = paths.get(address).isEmpty();
            final Channel channel = getChildChannel(ctx, address);
            if (paths.put(address, path) && firstPath && channel != null) {
                channel.pipeline().fireUserEventTriggered(ChannelDirectPathChanged.INSTANCE);
            }
        }

        private void removePath(final ChannelHandlerContext ctx,
                                final DrasylAddress address,
                                final Object path) {
            requireNonNull(address);
            requireNonNull(path);

            final Channel channel = getChildChannel(ctx, address);
            if (paths.remove(address, path) && paths.get(address).isEmpty() && channel != null) {
                channel.pipeline().fireUserEventTriggered(ChannelDirectPathChanged.INSTANCE);
            }
        }

        private static Channel getChildChannel(final ChannelHandlerContext ctx,
                                               final DrasylAddress remoteAddress) {
            final DrasylServerChannel parent = (DrasylServerChannel) ctx.channel();
            return parent.channels.get(remoteAddress);
        }
    }

    /**
     * This handler ensures that we have only one child channel per remote address at a time. If a
     * new child channel is created, the previous one will be closed.
     */
    private static class DuplicateChannelFilter extends SimpleChannelInboundHandler<DrasylChannel> {
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx,
                                    final DrasylChannel msg) {
            final DrasylChannel oldValue = ((DrasylServerChannel) ctx.channel()).channels.put(msg.remoteAddress(), msg);
            msg.closeFuture().addListener(f -> ((DrasylServerChannel) ctx.channel()).channels.remove(msg.remoteAddress()));
            if (oldValue != null) {
                oldValue.close();
                // wait for close to complete!?
            }

            ctx.fireChannelRead(msg);
        }
    }
}
