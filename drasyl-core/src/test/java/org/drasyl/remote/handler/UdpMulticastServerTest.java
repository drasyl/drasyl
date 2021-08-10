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
package org.drasyl.remote.handler;

import com.google.common.collect.ImmutableSet;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;

import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_INTERFACE;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdpMulticastServerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DatagramChannel channel;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Map<IdentityPublicKey, ChannelHandlerContext> nodes;

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnChannelActive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel datagramChannel) {
            when(bootstrap.handler(any()).bind(anyString(), anyInt())).thenReturn(channelFuture);
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel()).thenReturn(datagramChannel);
            when(datagramChannel.localAddress()).thenReturn(new InetSocketAddress(22527));
            when(config.getRemoteEndpoints()).thenReturn(ImmutableSet.of(Endpoint.of("udp://localhost:22527?publicKey=18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127")));
            when(datagramChannel.joinGroup(any(InetSocketAddress.class), any(NetworkInterface.class)).awaitUninterruptibly().isSuccess()).thenReturn(true);

            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrap, null);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelActive();

                verify(nodes).put(eq(identity.getIdentityPublicKey()), any());
                verify(bootstrap.handler(any())).bind(anyString(), anyInt());
                verify(datagramChannel).joinGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE);
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
            when(config.getRemoteEndpoints()).thenReturn(ImmutableSet.of(Endpoint.of("udp://localhost:22527?publicKey=18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127")));

            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrap, channel);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelInactive();

                verify(nodes).remove(identity.getIdentityPublicKey());
                verify(channel.leaveGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE)).awaitUninterruptibly();
                verify(channel.close()).awaitUninterruptibly();
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        @SuppressWarnings("unchecked")
        void shouldPassIngoingMessagesToAllPipelines(@Mock final ChannelHandlerContext channelCtx,
                                                     @Mock final ByteBuf message,
                                                     @Mock final IdentityPublicKey publicKey,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class));
            when(bootstrap.handler(any())).then((Answer<Bootstrap>) invocation -> {
                final SimpleChannelInboundHandler<DatagramPacket> handler = invocation.getArgument(0, SimpleChannelInboundHandler.class);
                handler.channelRead(channelCtx, new DatagramPacket(message, new InetSocketAddress(22527), new InetSocketAddress(25421)));
                return bootstrap;
            });

            final HashMap<IdentityPublicKey, ChannelHandlerContext> nodes = new HashMap<>(Map.of(publicKey, ctx));
            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrap, null);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelActive();

                verify(ctx).fireChannelRead(any());
            }
            finally {
                pipeline.releaseInbound();
                pipeline.close();
            }
        }
    }
}
