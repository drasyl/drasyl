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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static io.netty.util.CharsetUtil.UTF_8;
import static java.net.InetSocketAddress.createUnresolved;
import static java.time.Duration.ofSeconds;
import static org.drasyl.handler.remote.tcp.TcpServer.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpServerTest {
    private final Duration pingTimeout = ofSeconds(10);
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerBootstrap bootstrap;
    @Mock
    private Map<SocketAddress, Channel> clientChannels;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Channel serverChannel;
    private InetAddress bindHost;
    private int bindPort;
    private Function<ChannelHandlerContext, ChannelInitializer<SocketChannel>> channelInitializerSupplier;

    @BeforeEach
    void setUp() throws UnknownHostException {
        bindHost = InetAddress.getLocalHost();
        bindPort = 22527;
        channelInitializerSupplier = TcpServerChannelInitializer::new;
    }

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnChannelActive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(443));
            when(bootstrap.option(any(), any()).group(any()).channel(any()).childHandler(any()).bind(bindHost, bindPort).addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return null;
            });

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final TcpServer handler = new TcpServer(bootstrap, serverGroup, clientChannels, bindHost, bindPort, pingTimeout, channelInitializerSupplier, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                verify(bootstrap.option(any(), any()).group(any()).channel(any()).childHandler(any()), times(2)).bind(bindHost, bindPort);
            }
            finally {
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnChannelInactive(@Mock final ChannelHandlerContext ctx) {
            when(serverChannel.localAddress()).thenReturn(new InetSocketAddress(443));

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final TcpServer handler = new TcpServer(bootstrap, serverGroup, clientChannels, bindHost, bindPort, pingTimeout, channelInitializerSupplier, serverChannel);
            try {
                handler.channelInactive(ctx);

                verify(serverChannel).close();
            }
            finally {
                serverGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class MessagePassing {
        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldPassOutgoingMessageToTcpClient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage msg) {
            when(clientChannels.get(any())).thenReturn(client);

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final TcpServer handler = new TcpServer(bootstrap, serverGroup, clientChannels, bindHost, bindPort, pingTimeout, channelInitializerSupplier, serverChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                verify(client).writeAndFlush(any());
            }
            finally {
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldRejectMessageIfClientChannelIsNotWritable(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage msg) {
            when(clientChannels.get(any())).thenReturn(client);

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final TcpServer handler = new TcpServer(bootstrap, serverGroup, clientChannels, bindHost, bindPort, pingTimeout, channelInitializerSupplier, serverChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient), promise);
                assertFalse(promise.isSuccess());
            }
            finally {
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }

        @Test
        void shouldPassThroughOutgoingMessageForUnknownRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final TcpServer handler = new TcpServer(bootstrap, serverGroup, clientChannels, bindHost, bindPort, pingTimeout, channelInitializerSupplier, serverChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(msg, recipient), actual);

                actual.release();
            }
            finally {
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class TcpServerChannelInitializerTest {
        @Mock
        private Map<SocketAddress, Channel> clients;
        @Mock(answer = RETURNS_DEEP_STUBS)
        private ChannelHandlerContext ctx;

        @Test
        void shouldAddCorrectHandlersToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final SocketChannel ch,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final TcpServer tcpServer) {
            when(ctx.handler()).thenReturn(tcpServer);

            new TcpServerChannelInitializer(ctx).initChannel(ch);

            verify(ch.pipeline()).addLast(any(IdleStateHandler.class));
            verify(ch.pipeline()).addLast(any(TcpServerHandler.class));
        }
    }

    @Nested
    class TcpServerHandlerTest {
        @Captor
        ArgumentCaptor<ByteBuf> outboundMsg;
        @Mock
        private Map<SocketAddress, Channel> clients;
        @Mock(answer = RETURNS_DEEP_STUBS)
        private ChannelHandlerContext ctx;

        @Test
        void shouldAddClientOnNewConnection(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx) {
            new TcpServerHandler(clients, ctx).channelActive(nettyCtx);

            verify(clients).put(any(), any());
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldRemoveClientOnConnection(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx) {
            new TcpServerHandler(clients, ctx).channelInactive(nettyCtx);

            verify(clients).remove(any());
        }

        @Test
        void shouldPassInboundMessageToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor eventExecutor,
                                                @Mock InetSocketAddress recipient) {
            when(msg.readableBytes()).thenReturn(Integer.BYTES);
            when(msg.readInt()).thenReturn(RemoteMessage.MAGIC_NUMBER);
            when(ctx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));
            when(ctx.executor()).thenReturn(eventExecutor);

            new TcpDrasylMessageHandler().channelRead0(ctx, new InetAddressedMessage<>(msg, recipient));

            verify(ctx).fireChannelRead(any());
        }

        @Test
        void shouldRespondWithHttpAndCloseWhenInboundMessageIsInvalid(@Mock InetSocketAddress recipient) {
            when(ctx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));
            when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

            final ByteBuf msg = Unpooled.copiedBuffer("Hallo Welt", UTF_8);
            new TcpDrasylMessageHandler().channelRead0(ctx, new InetAddressedMessage<>(msg, recipient));

            verify(ctx).writeAndFlush(outboundMsg.capture());
            final ByteBuf httpOk = Unpooled.buffer().writeBytes(HTTP_OK);
            assertEquals(outboundMsg.getValue(), httpOk);

            httpOk.release();
            outboundMsg.getValue().release();
        }

        @Test
        void shouldCloseConnectionOnInactivity(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final IdleStateEvent evt) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            new TcpCloseIdleClientsHandler().userEventTriggered(nettyCtx, evt);

            verify(nettyCtx).close();
        }
    }
}
