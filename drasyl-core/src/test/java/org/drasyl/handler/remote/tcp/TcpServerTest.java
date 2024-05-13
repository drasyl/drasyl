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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.IdentityPublicKey;
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
import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;

import static io.netty.util.CharsetUtil.UTF_8;
import static java.net.InetSocketAddress.createUnresolved;
import static org.drasyl.handler.remote.tcp.TcpServer.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpServerTest {
    @Mock(answer = RETURNS_SELF)
    private ServerBootstrap bootstrap;
    @Mock
    private Map<IdentityPublicKey, SocketChannel> clientChannels;
    private InetAddress bindHost;
    private int bindPort;
    private Function<DrasylServerChannel, ChannelInitializer<SocketChannel>> channelInitializerSupplier;

    @BeforeEach
    void setUp() throws UnknownHostException {
        bindHost = InetAddress.getLocalHost();
        bindPort = 22527;
        channelInitializerSupplier = TcpServerChannelInitializer::new;
    }

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnChannelActive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final DrasylServerChannelConfig config) {
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(443));
            when(bootstrap.bind(any(InetAddress.class), anyInt())).thenReturn(channelFuture);
            when(bootstrap.bind(bindHost, bindPort).addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return null;
            });

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final TcpServer handler = new TcpServer(channelInitializerSupplier, null, clientChannels);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config);
            channel.pipeline().addLast(handler);
            try {
                verify(bootstrap).bind(bindHost, bindPort);
            }
            finally {
                channel.checkException();
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class TcpServerHandlerTest {
        @Captor
        ArgumentCaptor<ByteBuf> outboundMsg;
        @Mock(answer = RETURNS_DEEP_STUBS)
        private ChannelHandlerContext ctx;

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
