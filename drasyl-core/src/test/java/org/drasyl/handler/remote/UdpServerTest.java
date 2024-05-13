/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdpServerTest {
    @Mock(answer = RETURNS_SELF)
    private Bootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DatagramChannel udpChannel;
    private InetSocketAddress bindAddress;
    private Function<DrasylServerChannel, ChannelInitializer<DatagramChannel>> channelInitializerSupplier;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;

    @BeforeEach
    void setUp() {
        bindAddress = new InetSocketAddress(22527);
        channelInitializerSupplier = UdpServerChannelInitializer::new;
    }

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnChannelActive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(bootstrap.bind(any(InetSocketAddress.class))).thenReturn(channelFuture);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(22527));
            when(bootstrap.bind(bindAddress).addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return null;
            });

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final UdpServer handler = new UdpServer(channelInitializerSupplier, null);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config);
            channel.pipeline().addLast(handler);
            try {
                verify(bootstrap).bind(bindAddress);
            }
            finally {
                channel.checkException();
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPassOutgoingMessagesToUdp(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage msg,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final UdpServerToDrasylHandler udpServerToDrasylHandler) {
            when(udpChannel.pipeline().get(any(Class.class))).thenReturn(udpServerToDrasylHandler);

            final InetSocketAddress recipient = new InetSocketAddress(1234);

            final NioEventLoopGroup serverGroup = new NioEventLoopGroup(1);
            final UdpServer handler = new UdpServer(channelInitializerSupplier, udpChannel);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config);
            channel.pipeline().addLast(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                verify(udpServerToDrasylHandler).enqueueWrite(any());
            }
            finally {
                channel.checkException();
                channel.close();
                serverGroup.shutdownGracefully();
            }
        }
    }
}
