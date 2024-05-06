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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.FutureListenerUtil.fireExceptionToChannelOnFailure;
import static org.drasyl.util.InetSocketAddressUtil.equalSocketAddress;
import static org.drasyl.util.InetSocketAddressUtil.replaceSocketAddressPort;

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
    private final Function<DrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier;
    private final LongSupplier currentTime;
    private final AtomicLong lastSuperPeerAcknowledgementTime;
    private final Map<InetSocketAddress, SocketChannel> tcpChannels;
    ScheduledFuture<?> checkDisposable;
    boolean clientsStarted;

    TcpClient(final Function<DrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier,
              final LongSupplier currentTime,
              final AtomicLong lastSuperPeerAcknowledgementTime,
              final Map<InetSocketAddress, SocketChannel> tcpChannels,
              final ScheduledFuture<?> checkDisposable,
              final boolean clientsStarted) {
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
        this.currentTime = requireNonNull(currentTime);
        this.lastSuperPeerAcknowledgementTime = requireNonNull(lastSuperPeerAcknowledgementTime);
        this.tcpChannels = requireNonNull(tcpChannels);
        this.checkDisposable = checkDisposable;
        this.clientsStarted = clientsStarted;
    }

    public TcpClient(final Function<DrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        this(channelInitializerSupplier, System::currentTimeMillis, new AtomicLong(), new HashMap<>(), null, false);
    }

    public TcpClient() {
        this(TcpClientChannelInitializer::new);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            lastSuperPeerAcknowledgementTime.set(currentTime.getAsLong());
            startUnreachableSuperPeersCheck(ctx);
        }

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stopUnreachableSuperPeersCheck();

        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        ctx.fireChannelRead(msg);

        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).sender().getPort() != config(ctx).getTcpClientConnectPort() && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage && config(ctx).getSuperPeers().containsKey(((RemoteMessage) ((InetAddressedMessage<?>) msg).content()).getSender())) {
            lastSuperPeerAcknowledgementTime.set(currentTime.getAsLong());
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof RemoteMessage) {
            final Optional<Entry<InetSocketAddress, SocketChannel>> tcpChannelEntry = tcpChannels.entrySet().stream().filter(entry -> equalSocketAddress(((InetAddressedMessage<?>) msg).recipient(), entry.getKey()) && entry.getValue().isOpen()).findFirst();
            tcpChannelEntry.ifPresent(entry -> entry.getValue().pipeline().get(TcpClientToDrasylHandler.class).enqueueWrite(msg));
        }
        else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx) throws Exception {
        tcpChannels.values().forEach(channel -> {
            if (channel.isOpen()) {
                final TcpClientToDrasylHandler outboundBufferHolder = channel.pipeline().get(TcpClientToDrasylHandler.class);
                outboundBufferHolder.finishWrite();
            }
        });

        ctx.flush();
    }

    private void startUnreachableSuperPeersCheck(ChannelHandlerContext ctx) {
        if (checkDisposable == null) {
            LOG.debug("Start unreachable super peer check.");
            checkDisposable = ctx.executor().scheduleWithFixedDelay(() -> checkForUnreachableSuperPeers(ctx), config(ctx).getHelloTimeout().toMillis(), config(ctx).getHelloTimeout().toMillis(), MILLISECONDS);
            checkDisposable.addListener(fireExceptionToChannelOnFailure(ctx.channel()));
        }
    }

    private void stopUnreachableSuperPeersCheck() {
        if (checkDisposable != null) {
            LOG.debug("Stop unreachable super peer check.");
            checkDisposable.cancel(false);
            checkDisposable = null;
        }
    }

    private void checkForUnreachableSuperPeers(final ChannelHandlerContext ctx) {
        if (currentTime.getAsLong() - lastSuperPeerAcknowledgementTime.get() > config(ctx).getHelloTimeout().toMillis()) {
            startClients(ctx);
        }
        else {
            stopClients();
        }
    }

    @SuppressWarnings({ "java:S1905", "java:S3824" })
    private void startClients(final ChannelHandlerContext ctx) {
        if (!clientsStarted) {
            clientsStarted = true;
            LOG.debug("No response from any super peer since more then {}ms. UDP traffic" +
                    " blocked!? Try to reach a super peers via TCP.", () -> config(ctx).getHelloTimeout().toMillis());

            final Bootstrap bootstrap = config(ctx).getTcpClientBootstrap().get()
                    .group(config(ctx).getTcpClientEventLoop().get())
                    .channel(config(ctx).getTcpClientChannelClass())
                    .handler(channelInitializerSupplier.apply((DrasylServerChannel) ctx.channel()));

            for (Entry<IdentityPublicKey, InetSocketAddress> entry : config(ctx).getSuperPeers().entrySet()) {
                final IdentityPublicKey superPeerKey = entry.getKey();
                final InetSocketAddress superPeerAddress = entry.getValue();
                DrasylServerChannelConfig drasylServerChannelConfig = config(ctx);
                InetSocketAddress superPeerTcpAddress = replaceSocketAddressPort(superPeerAddress, drasylServerChannelConfig.getTcpClientConnectPort());

                bootstrap.connect(superPeerTcpAddress)
                        .addListener(new TcpClientConnectListener((DrasylServerChannel) ctx.channel(), superPeerKey, superPeerAddress));
            }
        }
    }

    private void stopClients() {
        if (clientsStarted) {
            clientsStarted = false;
            tcpChannels.values().forEach(ChannelOutboundInvoker::close);
        }
    }

    protected static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }

    /**
     * Listener that gets called once the channel is connected.
     */
    private class TcpClientConnectListener implements ChannelFutureListener {
        private final DrasylServerChannel parent;
        private final IdentityPublicKey publicKey;
        private final InetSocketAddress udpEndpoint;

        public TcpClientConnectListener(final DrasylServerChannel parent,
                                        final IdentityPublicKey publicKey,
                                        final InetSocketAddress udpEndpoint) {
            this.parent = requireNonNull(parent);
            this.publicKey = requireNonNull(publicKey);
            this.udpEndpoint = requireNonNull(udpEndpoint);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // client successfully started
                final SocketChannel channel = (SocketChannel) future.channel();
                final InetSocketAddress socketAddress = channel.remoteAddress();
                LOG.info("Client started and connected to tcp:/{}.", socketAddress);

                TcpClient.this.tcpChannels.put(udpEndpoint, channel);
                parent.pipeline().fireUserEventTriggered(new TcpClientConnected(publicKey, socketAddress));

                channel.closeFuture().addListener(new TcpClientCloseListener());
                parent.closeFuture().addListener(new DrasylServerChannelCloseListener(channel));
            }
            else {
                // server start failed
                parent.pipeline().fireExceptionCaught(new TcpClientConnectFailedException("Unable to connect client to address tcp:/" + future.channel().remoteAddress(), future.cause()));
            }
        }
    }

    /**
     * Signals that the {@link TcpClient} was unable to connect to given address.
     */
    public static class TcpClientConnectFailedException extends Exception {
        public TcpClientConnectFailedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Signals that the {@link TcpClient} is connected to {@link TcpClientConnected#getConnectAddress()}.
     */
    public static class TcpClientConnected {
        private final IdentityPublicKey publicKey;
        private final InetSocketAddress connectAddress;

        public TcpClientConnected(final IdentityPublicKey publicKey, final InetSocketAddress connectAddress) {
            this.publicKey = requireNonNull(publicKey);
            this.connectAddress = requireNonNull(connectAddress);
        }

        public IdentityPublicKey getPublicKey() {
            return publicKey;
        }

        public InetSocketAddress getConnectAddress() {
            return connectAddress;
        }
    }

    /**
     * Listener that gets called once the channel is closed.
     */
    private static class TcpClientCloseListener implements ChannelFutureListener {
        @Override
        public void operationComplete(final ChannelFuture future) {
            LOG.debug("Client connected tcp:/{} stopped.", future.channel().remoteAddress());
        }
    }

    /**
     * Listener that gets callec once DrasylServerChannel is closed.
     */
    private static class DrasylServerChannelCloseListener implements ChannelFutureListener {
        private final SocketChannel tcpChannel;

        public DrasylServerChannelCloseListener(final SocketChannel tcpChannel) {
            this.tcpChannel = requireNonNull(tcpChannel);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (tcpChannel.isOpen()) {
                LOG.debug("Stop client connected to tcp:/{}...", tcpChannel.remoteAddress());
                tcpChannel.close();
            }
        }
    }

}
