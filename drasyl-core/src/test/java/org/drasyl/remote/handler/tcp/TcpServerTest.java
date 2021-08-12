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
package org.drasyl.remote.handler.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.UserEventAwareEmbeddedChannel;
import org.drasyl.remote.handler.tcp.TcpServer.TcpServerChannelInitializer;
import org.drasyl.remote.handler.tcp.TcpServer.TcpServerHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;

import static io.netty.util.CharsetUtil.UTF_8;
import static java.net.InetSocketAddress.createUnresolved;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
            when(bootstrap.childHandler(any()).bind(any(InetAddress.class), anyInt())).thenReturn(channelFuture);
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(443));

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, null);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                verify(bootstrap.childHandler(any())).bind(any(InetAddress.class), anyInt());
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnChannelInactive() {
            when(serverChannel.localAddress()).thenReturn(new InetSocketAddress(443));

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                pipeline.pipeline().fireChannelInactive();

                verify(serverChannel).close();
            }
            finally {
                pipeline.close();
            }
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
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                pipeline.writeAndFlush(new AddressedMessage<>(msg, recipient));

                verify(client).writeAndFlush(any());
            }
            finally {
                pipeline.close();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldRejectMessageIfClientChannelIsNotWritable(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(clientChannels.get(any())).thenReturn(client);

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final ChannelPromise promise = pipeline.newPromise();
                pipeline.writeAndFlush(new AddressedMessage<>(msg, recipient), promise);
                assertFalse(promise.isSuccess());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldPassThroughOutgoingMessageForUnknownRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            final TcpServer handler = new TcpServer(bootstrap, clientChannels, bindHost, bindPort, pingTimeout, serverChannel);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                pipeline.writeAndFlush(new AddressedMessage<>(msg, recipient));

                final ReferenceCounted actual = pipeline.readOutbound();
                assertEquals(new AddressedMessage<>(msg, recipient), actual);

                actual.release();
            }
            finally {
                pipeline.close();
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
            new TcpServerChannelInitializer(clients, ctx, pingTimeout).initChannel(ch);

            verify(ch.pipeline()).addLast(any(IdleStateHandler.class));
            verify(ch.pipeline()).addLast(any(TcpServerHandler.class));
        }
    }

    @Nested
    class TcpServerHandlerTest {
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
        void shouldPassInboundMessageToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            new TcpServerHandler(clients, ctx).channelRead0(nettyCtx, msg);

            verify(ctx).fireChannelRead(any());
        }

        @Test
        void shouldCloseConnectionWhenInboundMessageIsInvalid(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            final ByteBuf msg = Unpooled.copiedBuffer("Hallo Welt", UTF_8);
            new TcpServerHandler(clients, ctx).channelRead0(nettyCtx, msg);

            verify(nettyCtx).close();
        }

        @Test
        void shouldCloseConnectionOnInactivity(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final IdleStateEvent evt) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            new TcpServerHandler(clients, ctx).userEventTriggered(nettyCtx, evt);

            verify(nettyCtx).close();
        }
    }
}
