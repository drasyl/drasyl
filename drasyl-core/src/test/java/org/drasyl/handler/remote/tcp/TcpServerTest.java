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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
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
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;

import static io.netty.util.CharsetUtil.UTF_8;
import static java.net.InetSocketAddress.createUnresolved;
import static java.time.Duration.ofSeconds;
import static org.drasyl.handler.remote.tcp.TcpServer.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpServerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerBootstrap bootstrap;
    @Mock
    private Map<SocketAddress, Channel> clientChannels;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Channel serverChannel;
    private InetAddress bindHost;
    private int bindPort;
    private final Duration pingTimeout = ofSeconds(10);

    @BeforeEach
    void setUp() throws UnknownHostException {
        bindHost = InetAddress.getLocalHost();
        bindPort = 22527;
    }

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnChannelActive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(443));
            when(bootstrap.group(any()).channel(any()).childHandler(any()).bind(any(InetAddress.class), anyInt()).addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return null;
            });

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                verify(bootstrap.group(any()).channel(any()).childHandler(any())).bind(any(InetAddress.class), anyInt());
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnChannelInactive(@Mock final ChannelHandlerContext ctx) {
            when(serverChannel.localAddress()).thenReturn(new InetSocketAddress(443));

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            handler.channelInactive(ctx);

            verify(serverChannel).close();
        }
    }

    @Nested
    class MessagePassing {
        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldPassOutgoingMessageToTcpClient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(clientChannels.get(any())).thenReturn(client);

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                verify(client).writeAndFlush(any());
            }
            finally {
                channel.close();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldRejectMessageIfClientChannelIsNotWritable(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(clientChannels.get(any())).thenReturn(client);

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ChannelPromise promise = channel.newPromise();
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient), promise);
                assertFalse(promise.isSuccess());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldPassThroughOutgoingMessageForUnknownRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(msg, recipient), actual);

                actual.release();
            }
            finally {
                channel.close();
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
        void shouldAddCorrectHandlersToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Channel ch) {
            new TcpServer.TcpServerChannelInitializer(clients, ctx, pingTimeout).initChannel(ch);

            verify(ch.pipeline()).addLast(any(IdleStateHandler.class));
            verify(ch.pipeline()).addLast(any(TcpServer.TcpServerHandler.class));
        }
    }

    @Nested
    class TcpServerHandlerTest {
        @Mock
        private Map<SocketAddress, Channel> clients;
        @Mock(answer = RETURNS_DEEP_STUBS)
        private ChannelHandlerContext ctx;
        @Captor
        ArgumentCaptor<ByteBuf> outboundMsg;

        @Test
        void shouldAddClientOnNewConnection(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx) {
            new TcpServer.TcpServerHandler(clients, ctx).channelActive(nettyCtx);

            verify(clients).put(any(), any());
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldRemoveClientOnConnection(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx) {
            new TcpServer.TcpServerHandler(clients, ctx).channelInactive(nettyCtx);

            verify(clients).remove(any());
        }

        @Test
        void shouldPassInboundMessageToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor eventExecutor) {
            when(msg.readableBytes()).thenReturn(Integer.BYTES);
            when(msg.readInt()).thenReturn(RemoteMessage.MAGIC_NUMBER);
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));
            when(ctx.executor()).thenReturn(eventExecutor);
            doAnswer((Answer<Object>) invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }).when(eventExecutor).execute(any());

            new TcpServer.TcpServerHandler(clients, ctx).channelRead0(nettyCtx, msg);

            verify(ctx).fireChannelRead(any());
        }

        @Test
        void shouldRespondWithHTTPAndCloseWhenInboundMessageIsInvalid(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            final ByteBuf msg = Unpooled.copiedBuffer("Hallo Welt", UTF_8);
            new TcpServer.TcpServerHandler(clients, ctx).channelRead0(nettyCtx, msg);

            verify(nettyCtx).writeAndFlush(outboundMsg.capture());
            assertEquals(outboundMsg.getValue(), Unpooled.buffer().writeBytes(HTTP_OK));
        }

        @Test
        void shouldCloseConnectionOnInactivity(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final IdleStateEvent evt) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            new TcpServer.TcpServerHandler(clients, ctx).userEventTriggered(nettyCtx, evt);

            verify(nettyCtx).close();
        }
    }
}
