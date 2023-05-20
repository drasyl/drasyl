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
package org.drasyl.handler.remote.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.InetSocketAddressUtil.equalSocketAddress;

/**
 * This handler monitors how long the node has not received a response from any super peer. If the
 * super peers have not responded for {@code drasyl.remote.tcp-fallback.client.timeout}, an attempt
 * is made to connect to {@code drasyl.remote.tcp-fallback.client.address} via TCP. If a TCP-based
 * connection can be established, all messages are also sent via TCP. As soon as a super peer
 * responds (again) via UDP, the TCP connection is closed.
 * <p>
 * This client is used as a last resort when otherwise no connection to a super peer can be
 * established (e.g. because the node operates in a very restrictive network that does not allow
 * UDP-based traffic). In this case, no direct connections to other peers are available and all
 * messages must be relayed through the fallback connection.
 * <p>
 * This client is only used if the node does not act as a super peer itself.
 */
@UnstableApi
@SuppressWarnings({ "java:S110" })
public class TcpClient extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TcpClient.class);
    private static final long RESOLVE_SUPER_PEER_ADDRESSES_INTERVAL = 60_000L;
    private final Set<InetSocketAddress> superPeerAddresses;
    private final Bootstrap bootstrap;
    private final EventLoopGroup group;
    private final AtomicLong noResponseFromSuperPeerSince;
    private final Duration timeout;
    private final InetSocketAddress address;
    private final Function<ChannelHandlerContext, ChannelInitializer<SocketChannel>> channelInitializerSupplier;
    private ChannelFuture superPeerChannel;
    private long lastSuperPeersResolveTime;

    /**
     * @param group                      the {@link NioEventLoopGroup} the underlying tcp client
     *                                   should run on
     * @param channelInitializerSupplier
     */
    @SuppressWarnings("java:S107")
    TcpClient(final Set<InetSocketAddress> superPeerAddresses,
              final Bootstrap bootstrap,
              final NioEventLoopGroup group,
              final AtomicLong noResponseFromSuperPeerSince,
              final Duration timeout,
              final InetSocketAddress address,
              final Function<ChannelHandlerContext, ChannelInitializer<SocketChannel>> channelInitializerSupplier,
              final ChannelFuture superPeerChannel) {
        this.superPeerAddresses = requireNonNull(superPeerAddresses);
        this.bootstrap = requireNonNull(bootstrap);
        this.group = requireNonNull(group);
        this.noResponseFromSuperPeerSince = requireNonNull(noResponseFromSuperPeerSince);
        this.timeout = requireNonNull(timeout);
        this.address = requireNonNull(address);
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.superPeerChannel = superPeerChannel;
    }

    /**
     * @param group the {@link NioEventLoopGroup} the underlying tcp client should run on
     */
    public TcpClient(final NioEventLoopGroup group,
                     final Set<InetSocketAddress> superPeerAddresses,
                     final Duration timeout,
                     final InetSocketAddress address) {
        this(
                superPeerAddresses,
                new Bootstrap(),
                group,
                new AtomicLong(),
                timeout,
                address,
                TcpClientChannelInitializer::new,
                null
        );
    }

    private void stopClient() {
        if (superPeerChannel != null) {
            superPeerChannel.cancel(true);

            if (superPeerChannel.isSuccess()) {
                superPeerChannel.channel().close();
            }
            superPeerChannel = null;
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        ctx.fireChannelRead(msg);

        if (msg instanceof InetAddressedMessage) {
            checkForReachableSuperPeer(((InetAddressedMessage<?>) msg).sender());
        }
    }

    /**
     * This method is called whenever a message is received. It checks whether a message has been
     * received from a super peer and closes the fallback TCP connection if necessary.
     */
    private void checkForReachableSuperPeer(final InetSocketAddress sender) {
        // message from super peer?
        if (superPeerAddresses.contains(sender)) {
            // super peer(s) reachable via udp -> close fallback connection!
            noResponseFromSuperPeerSince.set(0);
            stopClient();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        resolveSuperPeers();

        if (msg instanceof InetAddressedMessage &&
                superPeerAddresses.stream().anyMatch(socketAddress -> equalSocketAddress(socketAddress, ((InetAddressedMessage<?>) msg).recipient())) &&
                ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            // check if we can route the message via a tcp connection
            final ChannelFuture mySuperPeerChannel = this.superPeerChannel;
            if (mySuperPeerChannel != null && mySuperPeerChannel.isSuccess()) {
                LOG.trace("Send message `{}` via TCP connection.", () -> msg);
                PromiseNotifier.cascade(mySuperPeerChannel.channel().write(msg), promise);
            }
            else {
                // pass through message
                ctx.write(msg, promise);

                checkForUnreachableSuperPeers();
            }
        }
        else {
            // pass through message
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        final ChannelFuture mySuperPeerChannel = this.superPeerChannel;
        if (mySuperPeerChannel != null && mySuperPeerChannel.isSuccess()) {
            mySuperPeerChannel.channel().flush();
        }

        ctx.flush();
    }

    private void resolveSuperPeers() {
        final long currentTimeMillis = System.currentTimeMillis();
        if (lastSuperPeersResolveTime < currentTimeMillis - RESOLVE_SUPER_PEER_ADDRESSES_INTERVAL) {
            lastSuperPeersResolveTime = currentTimeMillis;
            final Set<InetSocketAddress> newAddresses = new HashSet<>();
            for (final InetSocketAddress superPeerAddress : superPeerAddresses) {
                newAddresses.add(new InetSocketAddress(superPeerAddress.getHostString(), superPeerAddress.getPort()));
            }
            superPeerAddresses.clear();
            superPeerAddresses.addAll(newAddresses);
        }
    }

    /**
     * This method is called every time a message is sent. It checks how long no response was
     * received from a super peer and then tries to establish a fallback TCP connection if
     * necessary.
     */
    private void checkForUnreachableSuperPeers() {
        final long currentTimeMillis = System.currentTimeMillis();
        noResponseFromSuperPeerSince.compareAndSet(0, currentTimeMillis);
        if (noResponseFromSuperPeerSince.get() < currentTimeMillis - timeout.toMillis()) {
            // no response from super peer(s) for a too long duration -> establish fallback connection!
            startClient();
        }
    }

    @SuppressWarnings({ "java:S1905", "java:S3824" })
    private void startClient() {
        if (superPeerChannel == null) {
            final long currentTime = System.currentTimeMillis();
            LOG.debug("No response from any super peer since {}ms. UDP traffic" +
                    " blocked!? Try to reach a super peer via TCP.", () -> currentTime - noResponseFromSuperPeerSince.get());

            // reset counter so that no connection is attempted more often then defined in `drasyl.remote.tcp-fallback.client.timeout`
            noResponseFromSuperPeerSince.set(currentTime);

            superPeerChannel = bootstrap.connect(address);
            superPeerChannel.addListener(new TcpClientFutureListener());
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.fireChannelActive();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(channelInitializerSupplier.apply(ctx));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();

        // stop client
        stopClient();
    }

    private class TcpClientFutureListener implements ChannelFutureListener {
        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                final Channel channel = future.channel();
                LOG.debug("TCP connection to `{}` established.", address);
                channel.closeFuture().addListener(future1 -> {
                    LOG.debug("TCP connection to `{}` closed.", address);
                    superPeerChannel = null;
                });
            }
            else {
                LOG.debug("Unable to establish TCP connection to `{}`:", () -> address, future::cause);
                superPeerChannel = null;
            }
        }
    }
}
