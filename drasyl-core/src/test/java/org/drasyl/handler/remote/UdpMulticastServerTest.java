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
package org.drasyl.handler.remote;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdpMulticastServerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Supplier<Bootstrap> bootstrapSupplier;
    @Mock(answer = RETURNS_SELF)
    private Bootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DatagramChannel channel;
    @Mock
    private Set<ChannelHandlerContext> nodes;
    private Function<ChannelHandlerContext, ChannelInitializer<DatagramChannel>> channelInitializerSupplier;

    @BeforeEach
    void setUp() {
        channelInitializerSupplier = UdpMulticastServerChannelInitializer::new;
    }

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnChannelActive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel datagramChannel) {
            when(bootstrapSupplier.get()).thenReturn(bootstrap);
            when(bootstrap.bind(anyString(), anyInt())).thenReturn(channelFuture);
            when(channelFuture.channel()).thenReturn(datagramChannel);
            when(datagramChannel.localAddress()).thenReturn(new InetSocketAddress(22527));
            when(datagramChannel.joinGroup(any(InetSocketAddress.class), any(NetworkInterface.class)).addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(null);
                return null;
            });

            final NioEventLoopGroup multicastServerGroup = new NioEventLoopGroup(1);
            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrapSupplier, multicastServerGroup, channelInitializerSupplier);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                verify(nodes).add(channel.pipeline().context(handler));
                verify(bootstrap.group(any()).channelFactory(any()).handler(any())).bind(anyString(), anyInt());
            }
            finally {
                channel.checkException();
                channel.close();
                multicastServerGroup.shutdownGracefully();
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnChannelInactive() {
            when(nodes.isEmpty()).thenReturn(true);
            when(UdpMulticastServerTest.this.channel.leaveGroup(any(InetSocketAddress.class), any()).addListener(any())).then(invocation -> {
                final GenericFutureListener<?> listener = invocation.getArgument(0, GenericFutureListener.class);
                listener.operationComplete(null);
                return null;
            });

            final NioEventLoopGroup multicastServerGroup = new NioEventLoopGroup(1);
            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrapSupplier, multicastServerGroup, channelInitializerSupplier, channel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelInactive();

                verify(nodes).remove(channel.pipeline().context(handler));
                verify(UdpMulticastServerTest.this.channel).leaveGroup(any(InetSocketAddress.class), any());
                verify(UdpMulticastServerTest.this.channel).close();
            }
            finally {
                channel.checkException();
                channel.close();
                multicastServerGroup.shutdownGracefully();
            }
        }
    }
}
