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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.handler.remote.PeersManager.PathId;
import org.drasyl.handler.remote.UdpServer;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler;
import org.drasyl.util.internal.UnstableApi;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
    public static final PathId PATH_ID = new PathId() {
        @Override
        public short priority() {
            return 110;
        }
    };
    private final Function<DrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier;
    ScheduledFuture<?> checkDisposable;
    private SocketChannel tcpChannel;

    /**
     * @param channelInitializerSupplier
     */
    @SuppressWarnings("java:S107")
    TcpClient(final Function<DrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier) {
        this.channelInitializerSupplier = requireNonNull(channelInitializerSupplier);
    }

    /**
     */
    public TcpClient() {
        this(TcpClientChannelInitializer::new);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws UdpServer.UdpServerBindFailedException {
        if (ctx.channel().isActive()) {
            startUnreachableSuperPeersCheck(ctx);
        }

        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        stopUnreachableSuperPeersCheck();
        stopClient();

        ctx.fireChannelInactive();
    }

    private void startUnreachableSuperPeersCheck(ChannelHandlerContext ctx) {
        if (checkDisposable == null) {
            LOG.debug("Start unreachable super peer check.");
            checkDisposable = ctx.executor().scheduleWithFixedDelay(() -> checkForUnreachableSuperPeers(ctx), config(ctx).getHelloTimeout().toMillis(), config(ctx).getHelloTimeout().toMillis(), MILLISECONDS);
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
        if (config(ctx).getPeersManager().hasPath(InternetDiscoveryChildrenHandler.PATH_ID)) {
            stopClient();
        }
        else {
            startClient(ctx);
        }
    }

    @SuppressWarnings({ "java:S1905", "java:S3824" })
    private void startClient(final ChannelHandlerContext ctx) {
        LOG.debug("No response from any super peer since more then {}ms. UDP traffic" +
                " blocked!? Try to reach a super peer via TCP.", () -> config(ctx).getHelloTimeout().toMillis());

        config(ctx).getTcpClientBootstrap()
                .group(config(ctx).getTcpClientEventLoopSupplier().get())
                .channel(config(ctx).getTcpClientChannelClass())
                .handler(channelInitializerSupplier.apply((DrasylServerChannel) ctx.channel()))
                .connect(config(ctx).getTcpClientConnect())
                .addListener(new TcpClientConnectListener((DrasylServerChannel) ctx.channel()));
    }

    private void stopClient() {
        if (tcpChannel != null) {
            tcpChannel.close();
            tcpChannel = null;
        }
    }

    protected static DrasylServerChannelConfig config(final ChannelHandlerContext ctx) {
        return (DrasylServerChannelConfig) ctx.channel().config();
    }

    /**
     * Listener that gets called once the channel is closed.
     */
    private static class TcpClientCloseListener implements ChannelFutureListener {
        private final DrasylServerChannel parent;

        public TcpClientCloseListener(final DrasylServerChannel parent) {
            this.parent = requireNonNull(parent);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            final InetSocketAddress socketAddress = (InetSocketAddress) future.channel().remoteAddress();
            LOG.debug("Client connected tcp:/{} stopped.", socketAddress);

            parent.config().getPeersManager().unsetTcpFallback(parent.pipeline().context(TcpClient.class));
        }
    }

    /**
     * Signals that the {@link TcpClient} is connected to {@link TcpClientConnected#getConnectAddress()}.
     */
    public static class TcpClientConnected {
        private final InetSocketAddress connectAddress;

        public TcpClientConnected(final InetSocketAddress connectAddress) {
            this.connectAddress = requireNonNull(connectAddress);
        }

        public InetSocketAddress getConnectAddress() {
            return connectAddress;
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

    private class TcpClientConnectListener implements ChannelFutureListener {
        private final DrasylServerChannel parent;

        public TcpClientConnectListener(final DrasylServerChannel parent) {
            this.parent = requireNonNull(parent);
        }

        @Override
        public void operationComplete(final ChannelFuture future) {
            if (future.isSuccess()) {
                // server successfully started
                final Channel myChannel = future.channel();
                myChannel.closeFuture().addListener(new TcpClientCloseListener(parent));
                final InetSocketAddress socketAddress = (InetSocketAddress) myChannel.remoteAddress();
                LOG.info("Client started and connected to tcp:/{}.", socketAddress);

                TcpClient.this.tcpChannel = (SocketChannel) myChannel;
                parent.config().getPeersManager().setTcpFallback(parent.pipeline().context(TcpClient.class));

                parent.pipeline().fireUserEventTriggered(new TcpClientConnected(socketAddress));
            }
            else {
                // server start failed
                parent.pipeline().fireExceptionCaught(new TcpClientConnectFailedException("Unable to connect client to address tcp:/" + future.channel().remoteAddress(), future.cause()));
            }
        }
    }
}
