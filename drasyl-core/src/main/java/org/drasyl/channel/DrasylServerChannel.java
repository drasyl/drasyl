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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
public class DrasylServerChannel extends AbstractServerChannel {
    enum State {OPEN, ACTIVE, CLOSED}

    private static final Logger LOG = LoggerFactory.getLogger(DrasylServerChannel.class);
    private volatile State state;
    private final DefaultChannelConfig config = new DefaultChannelConfig(this);
    public final Map<SocketAddress, DrasylChannel> channels;
    private volatile DrasylAddress localAddress; // NOSONAR
    final SetMultimap<DrasylAddress, Object> paths = new HashSetMultimap<>();

    @SuppressWarnings("java:S2384")
    DrasylServerChannel(final State state,
                        final Map<SocketAddress, DrasylChannel> channels,
                        final DrasylAddress localAddress) {
        this.state = requireNonNull(state);
        this.channels = channels;
        this.localAddress = localAddress;
    }

    @SuppressWarnings("unused")
    public DrasylServerChannel() {
        this(State.OPEN, new ConcurrentHashMap<>(), null);
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof DefaultEventLoop;
    }

    @Override
    protected DrasylAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) {
        if (!(localAddress instanceof DrasylAddress)) {
            throw new IllegalArgumentException("Unsupported address type! Expected `" + DrasylAddress.class.getSimpleName() + "`, but got `" + localAddress.getClass().getSimpleName() + "`.");
        }

        this.localAddress = (DrasylAddress) localAddress;
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
                ch.pipeline().addLast(new PendingWritesFlusher());
            }
        });
    }

    @Override
    protected void doClose() {
        if (state != State.CLOSED) {
            // Update the internal state before the closeFuture<?> is notified.
            if (localAddress != null) {
                localAddress = null;
            }
            state = State.CLOSED;
        }
    }

    @Override
    protected void doBeginRead() {
        // NOOP
        // all inbound messages (UdpServer, UdpMulticastServer, TcpServer, TcpClient, ..) are
        // currently automatically pushed to us. no need to pull reads
    }

    @Override
    public DefaultChannelConfig config() {
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
                    // all child channels has been sucesfully closed
                    ctx.close(promise);
                }
                else {
                    // there was an error, close channel and return error then.
                    ctx.close().addListener(future2 -> promise.tryFailure(future.cause()));
                }
            });
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx,
                                final Object msg) {
            if (msg instanceof Channel) {
                // pass through
                ctx.fireChannelRead(msg);
            }
            else if (ctx.channel().isOpen()) {
                try {
                    final OverlayAddressedMessage<?> childMsg = (OverlayAddressedMessage<?>) msg;
                    final Object o = childMsg.content();
                    final IdentityPublicKey peer = (IdentityPublicKey) childMsg.sender();
                    passMessageToChannel(ctx, o, peer, true);
                }
                catch (final ClassCastException e) {
                    LOG.debug("Can't cast address of message `{}`: ", msg, e);
                }
            }
        }

        private static void passMessageToChannel(final ChannelHandlerContext ctx,
                                                 final Object o,
                                                 final IdentityPublicKey peer,
                                                 final boolean recreateClosedChannel) {
            final Channel channel = getOrCreateChildChannel(ctx, peer);

            // pass message to channel
            channel.eventLoop().execute(() -> {
                if (channel.isActive()) {
                    channel.pipeline().fireChannelRead(o);
                    channel.pipeline().fireChannelReadComplete();
                }
                else if (ctx.channel().isOpen() && recreateClosedChannel) {
                    // channel to which the message is to be passed to has been closed in the
                    // meantime. give message chance to be consumed by recreate a new channel once
                    ctx.executor().execute(() -> passMessageToChannel(ctx, o, peer, false));
                }
                else {
                    // drop message
                    ReferenceCountUtil.release(o);
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

        private static Channel getOrCreateChildChannel(final ChannelHandlerContext ctx,
                                                       final DrasylAddress remoteAddress) {
            final DrasylServerChannel parent = (DrasylServerChannel) ctx.channel();

            Channel channel = getChildChannel(ctx, remoteAddress);
            if (channel == null) {
                channel = new DrasylChannel(parent, remoteAddress);
                ctx.fireChannelRead(channel);
            }

            return channel;
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

    /**
     * This handler is part of the backpressure mechanisms of the server channel. It informs all
     * child channels to flush once the server channel has become writable again.
     */
    private static class PendingWritesFlusher extends ChannelInboundHandlerAdapter {
        @Override
        public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
            ctx.fireChannelWritabilityChanged();

            if (ctx.channel().isWritable()) {
                for (final DrasylChannel channel : ((DrasylServerChannel) ctx.channel()).channels.values()) {
                    if (channel.pendingWrites) {
                        channel.flush();
                    }
                }
            }
        }
    }
}
