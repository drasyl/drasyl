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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
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
import java.util.Set;

import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.drasyl.remote.handler.UdpMulticastServer.MULTICAST_INTERFACE;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private Map<CompressedPublicKey, HandlerContext> nodes;

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnNodeUpEvent(@Mock final NodeUpEvent event,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final DatagramChannel datagramChannel) {
            when(bootstrap.handler(any()).bind(anyString(), anyInt())).thenReturn(channelFuture);
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel()).thenReturn(datagramChannel);
            when(datagramChannel.localAddress()).thenReturn(new InetSocketAddress(22527));
            when(config.getRemoteEndpoints()).thenReturn(Set.of(Endpoint.of("udp://localhost:22527?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));
            when(datagramChannel.joinGroup(any(InetSocketAddress.class), any(NetworkInterface.class)).awaitUninterruptibly().isSuccess()).thenReturn(true);

            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrap, null);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(nodes).put(eq(identity.getPublicKey()), any());
                verify(bootstrap.handler(any())).bind(anyString(), anyInt());
                verify(datagramChannel).joinGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE);
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnNodeDownEvent(@Mock final NodeDownEvent event) {
            when(config.getRemoteEndpoints()).thenReturn(Set.of(Endpoint.of("udp://localhost:22527?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));

            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrap, channel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(nodes).remove(identity.getPublicKey());
                verify(channel.leaveGroup(MULTICAST_ADDRESS, MULTICAST_INTERFACE)).awaitUninterruptibly();
                verify(channel.close()).awaitUninterruptibly();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        @SuppressWarnings("unchecked")
        void shouldPassIngoingMessagesToAllPipelines(@Mock final NodeUpEvent event,
                                                     @Mock final ChannelHandlerContext channelCtx,
                                                     @Mock final ByteBuf message,
                                                     @Mock final CompressedPublicKey publicKey,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            when(bootstrap.handler(any())).then((Answer<Bootstrap>) invocation -> {
                final SimpleChannelInboundHandler<DatagramPacket> handler = invocation.getArgument(0, SimpleChannelInboundHandler.class);
                handler.channelRead(channelCtx, new DatagramPacket(message, new InetSocketAddress(22527), new InetSocketAddress(25421)));
                return bootstrap;
            });

            final HashMap<CompressedPublicKey, HandlerContext> nodes = new HashMap<>(Map.of(publicKey, ctx));
            final UdpMulticastServer handler = new UdpMulticastServer(nodes, bootstrap, null);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(ctx).passInbound(any(), any(), any());
            }
        }
    }
}
