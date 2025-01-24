/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.IntraVmDiscovery;
import org.drasyl.handler.discovery.PathEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.UdpServerToDrasylHandler;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
public class DrasylServerChannel extends AbstractServerChannel implements IdentityChannel {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylServerChannel.class);
    static Map<DrasylAddress, DrasylServerChannel> serverChannels = new ConcurrentHashMap<>();

    enum State {OPEN, ACTIVE, CLOSED}

    private final DrasylServerChannelConfig config = new DrasylServerChannelConfig(this);
    private final Map<DrasylAddress, DrasylChannel> channels;
    private volatile State state;
    private volatile Identity identity; // NOSONAR
    private DatagramChannel udpChannel;
    private volatile UdpServerToDrasylHandler udpDrasylHandler;
    private ChannelPromise activePromise;
    private ScheduledFuture<?> cachedTimeTask;
    private long cachedTimeMillis;

    @SuppressWarnings("java:S2384")
    DrasylServerChannel(final State state,
                        final Map<DrasylAddress, DrasylChannel> channels,
                        final Identity identity,
                        final UdpServerToDrasylHandler udpDrasylHandler,
                        final ChannelPromise activePromise,
                        final ScheduledFuture<?> cachedTimeTask) {
        this.state = requireNonNull(state);
        this.channels = requireNonNull(channels);
        this.identity = identity;
        this.udpDrasylHandler = udpDrasylHandler;
        this.activePromise = activePromise;
        this.cachedTimeTask = cachedTimeTask;
    }

    @SuppressWarnings("unused")
    public DrasylServerChannel() {
        this(State.OPEN, new ConcurrentHashMap<>(), null, null, null, null);
    }

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return true;
    }

    @Override
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

        if (config().isIntraVmDiscoveryEnabled()) {
            final PeersManager peersManager = config().getPeersManager();
            serverChannels.forEach((peerKey, peerChannel) -> {
                if (peerChannel.config().isIntraVmDiscoveryEnabled() && config().getNetworkId() == peerChannel.config().getNetworkId()) {
                    final PeersManager peerPeersManager = peerChannel.config().getPeersManager();
                    activePromise.addListener((ChannelFutureListener) future -> peersManager.addChildrenPath(pipeline().firstContext(), peerKey, IntraVmDiscovery.PATH_ID, null));
                    peerChannel.activePromise.addListener((ChannelFutureListener) future -> peerPeersManager.addChildrenPath(peerChannel.pipeline().firstContext(), DrasylServerChannel.this.identity.getAddress(), IntraVmDiscovery.PATH_ID, null));
                }
            });
            serverChannels.put(this.identity.getAddress(), this);
        }
    }

    @Override
    protected void doRegister() throws Exception {
        super.doRegister();

        cachedTimeTask = eventLoop().scheduleAtFixedRate(() -> {
            cachedTimeMillis = System.currentTimeMillis();
        }, 0, 1000, MILLISECONDS);

        activePromise = newPromise();

        pipeline().addLast(new ChannelInitializer<>() {
            @Override
            public void initChannel(final Channel ch) {
                ch.pipeline().addLast(new ChildChannelRouter());
                ch.pipeline().addLast(new DuplicateChannelFilter());
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(final ChannelHandlerContext ctx) {
                        ctx.fireChannelActive();

                        ctx.executor().execute(() -> activePromise.setSuccess());
                        ctx.pipeline().remove(this);
                    }
                });
            }
        });
    }

    @Override
    protected void doClose() {
        if (state != State.CLOSED) {
            // Update the internal state before the closeFuture<?> is notified.
            if (config().isIntraVmDiscoveryEnabled()) {
                serverChannels.remove(identity.getAddress());
            }

            if (identity != null) {
                identity = null;
            }
            state = State.CLOSED;

            cachedTimeTask.cancel(false);
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
        return new DrasylChannel(this, peer);
    }

    public Map<DrasylAddress, DrasylChannel> getChannels() {
        return channels;
    }

    public DrasylChannel getChannel(final DrasylAddress peer) {
        return channels.get(peer);
    }

    public ChannelFuture serve(final DrasylAddress peer) {
        final DrasylChannel channel;
        if (isOpen()) {
            channel = channels.computeIfAbsent(peer, k -> {
                final DrasylChannel ch = newDrasylChannel(k);
                pipeline().fireChannelRead(ch);
                pipeline().fireChannelReadComplete();
                return ch;
            });
        }
        else {
            channel = channels.get(peer);
        }

        if (channel != null) {
            return channel.registeredPromise;
        }
        else {
            return newFailedFuture(new Exception("DrasylServerChannel is closed and no DrasylChannel exist."));
        }
    }

    public DatagramChannel udpChannel() {
        if (udpChannel == null) {
            final UdpServer udpServer = pipeline().get(UdpServer.class);
            udpChannel = udpServer.udpChannel();
        }
        return udpChannel;
    }

    private UdpServerToDrasylHandler udpChannelHandler() {
        if (udpDrasylHandler == null) {
            udpDrasylHandler = udpChannel().pipeline().get(UdpServerToDrasylHandler.class);
        }
        return udpDrasylHandler;
    }

    public void flushMeIfUdpChannelBecomeWritable(final Channel channel) {
        udpChannelHandler().flushIfBecomeWritable(channel);
    }

    public long cachedTimeMillis() {
        return cachedTimeMillis;
    }

    /**
     * This handler routes inbound messages and events to the correct child channel. If there is
     * currently no child channel, a new one is automatically created.
     */
    private static class ChildChannelRouter extends ChannelDuplexHandler {
        @SuppressWarnings("java:S2384")
        ChildChannelRouter() {
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

                    if (pathEvent instanceof AddPathAndSuperPeerEvent || pathEvent instanceof AddPathAndChildrenEvent) {
                        addPath(ctx, peer, pathEvent.getPath());
                    }
                    else if (pathEvent instanceof RemoveSuperPeerAndPathEvent || pathEvent instanceof RemoveChildrenAndPathEvent) {
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

            final Channel channel = getChildChannel(ctx, address);
            if (config(ctx).getPeersManager().hasPath(address) && channel != null) {
                channel.pipeline().fireUserEventTriggered(ChannelDirectPathChanged.INSTANCE);
            }
        }

        private static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
            return (DrasylServerChannelConfig) ctx.channel().config();
        }

        private void removePath(final ChannelHandlerContext ctx,
                                final DrasylAddress address,
                                final Object path) {
            requireNonNull(address);
            requireNonNull(path);

            final Channel channel = getChildChannel(ctx, address);
            if (!config(ctx).getPeersManager().hasPath(address) && channel != null) {
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
            msg.closeFuture().addListener(f -> ((DrasylServerChannel) ctx.channel()).channels.remove(msg.remoteAddress()));
            ctx.fireChannelRead(msg);
        }
    }
}
