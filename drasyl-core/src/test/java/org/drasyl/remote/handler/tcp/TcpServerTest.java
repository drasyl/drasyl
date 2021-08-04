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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DefaultEmbeddedPipeline;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.handler.tcp.TcpServer.TcpServerChannelInitializer;
import org.drasyl.remote.handler.tcp.TcpServer.TcpServerHandler;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static java.net.InetSocketAddress.createUnresolved;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpServerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ServerBootstrap bootstrap;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Map<SocketAddress, Channel> clientChannels;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Channel serverChannel;

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnNodeUpEvent(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(bootstrap.childHandler(any()).bind(any(InetAddress.class), anyInt())).thenReturn(channelFuture);
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(443));

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, null);
            final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                verify(bootstrap.childHandler(any())).bind(any(InetAddress.class), anyInt());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
            when(serverChannel.localAddress()).thenReturn(new InetSocketAddress(443));

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, serverChannel);
            final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                verify(serverChannel).close();
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldStopServerOnNodeDownEvent(@Mock final NodeDownEvent event) {
            when(serverChannel.localAddress()).thenReturn(new InetSocketAddress(443));

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, serverChannel);
            final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                verify(serverChannel).close();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class MessagePassing {
        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldPassOutgoingMessageToTcpClient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper recipient,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg,
                                                  @Mock final ChannelFuture channelFuture) {
            when(clientChannels.get(any())).thenReturn(client);
            when(client.isWritable()).thenReturn(true);
            when(client.writeAndFlush(any())).thenReturn(channelFuture);
            when(channelFuture.isDone()).thenReturn(true);
            when(channelFuture.isSuccess()).thenReturn(true);

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, serverChannel);
            final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler);
            try {
                pipeline.processOutbound(recipient, msg).join();

                verify(client).writeAndFlush(any());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldRejectMessageIfClientChannelIsNotWritable(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Channel client,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(clientChannels.get(any())).thenReturn(client);

            final TcpServer handler = new TcpServer(bootstrap, clientChannels, serverChannel);
            final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler);
            try {
                assertThrows(CompletionException.class, pipeline.processOutbound(recipient, msg)::join);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughOutgoingMessageForUnknownRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            final TcpServer handler = new TcpServer(bootstrap, clientChannels, serverChannel);
            final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler);
            try {
                final TestObserver<Object> outboundMessages = pipeline.drasylOutboundMessages().test();

                pipeline.processOutbound(recipient, msg).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class TcpServerChannelInitializerTest {
        @Mock
        private Map<SocketAddress, Channel> clients;
        @Mock(answer = RETURNS_DEEP_STUBS)
        private HandlerContext ctx;

        @Test
        void shouldAddCorrectHandlersToChannel(@Mock(answer = RETURNS_DEEP_STUBS) final Channel ch) {
            new TcpServerChannelInitializer(clients, ctx).initChannel(ch);

            verify(ch.pipeline()).addLast(any(IdleStateHandler.class));
            verify(ch.pipeline()).addLast(any(TcpServerHandler.class));
        }
    }

    @Nested
    class TcpServerHandlerTest {
        @Mock
        private Map<SocketAddress, Channel> clients;
        @Mock(answer = RETURNS_DEEP_STUBS)
        private HandlerContext ctx;

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

            verify(ctx).passInbound(any(InetSocketAddressWrapper.class), any(), any());
        }

        @Test
        void shouldCloseConnectionWhenInboundMessageIsInvalid(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));
            when(ctx.passInbound(any(), any(), any())).thenReturn(failedFuture(new Exception(new InvalidMessageFormatException())));

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
