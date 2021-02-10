/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.remote.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

import static java.net.InetSocketAddress.createUnresolved;
import static org.drasyl.remote.handler.UdpServer.determineActualEndpoints;
import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdpServerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Channel channel;
    @Mock
    private PeersManager peersManager;

    @Nested
    class StartServer {
        @Test
        void shouldStartServerOnNodeUpEvent(@Mock final NodeUpEvent event,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(bootstrap.handler(any()).bind(any(InetAddress.class), anyInt())).thenReturn(channelFuture);
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(22527));
            when(config.getRemoteEndpoints()).thenReturn(Set.of(Endpoint.of("udp://localhost:22527?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));

            final UdpServer handler = new UdpServer(bootstrap, null);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

            pipeline.processInbound(event).join();

            verify(bootstrap.handler(any())).bind(any(InetAddress.class), anyInt());
            pipeline.close();
        }

        @Nested
        class DetermineActualEndpoints {
            @Test
            void shouldReturnConfigEndpointsIfSpecified() {
                when(config.getRemoteEndpoints()).thenReturn(Set.of(Endpoint.of("udp://foo.bar:22527?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")));

                assertEquals(
                        Set.of(Endpoint.of("udp://foo.bar:22527?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")),
                        determineActualEndpoints(identity, config, new InetSocketAddress(22527))
                );
            }

            @Test
            void shouldReturnEndpointForSpecificAddressesIfServerIsBoundToSpecificInterfaces() {
                when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"));

                final InetAddress firstAddress = getAddresses().iterator().next();
                if (firstAddress != null) {
                    when(config.getRemoteEndpoints().isEmpty()).thenReturn(true);

                    assertEquals(
                            Set.of(Endpoint.of(firstAddress.getHostAddress(), 22527, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))),
                            determineActualEndpoints(identity, config, new InetSocketAddress(firstAddress, 22527))
                    );
                }
            }
        }
    }

    @Nested
    class StopServer {
        @Test
        void shouldStopServerOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
            when(channel.localAddress()).thenReturn(new InetSocketAddress(22527));

            final UdpServer handler = new UdpServer(bootstrap, channel);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

            pipeline.processInbound(event).join();

            verify(channel).close();
            pipeline.close();
        }

        @Test
        void shouldStopServerOnNodeDownEvent(@Mock final NodeDownEvent event) {
            when(channel.localAddress()).thenReturn(new InetSocketAddress(22527));

            final UdpServer handler = new UdpServer(bootstrap, channel);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

            pipeline.processInbound(event).join();

            verify(channel).close();
            pipeline.close();
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPassOutgoingMessagesToUdp(@Mock(answer = RETURNS_DEEP_STUBS) final AddressedByteBuf msg) {
            final InetSocketAddressWrapper recipient = new InetSocketAddressWrapper(22527);
            when(channel.isWritable()).thenReturn(true);
            when(msg.getRecipient()).thenReturn(new InetSocketAddressWrapper(1234));
            when(channel.writeAndFlush(any()).isDone()).thenReturn(true);
            when(channel.writeAndFlush(any()).isSuccess()).thenReturn(true);

            final UdpServer handler = new UdpServer(bootstrap, channel);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);

            pipeline.processOutbound(recipient, msg).join();

            verify(channel, times(3)).writeAndFlush(any());
            pipeline.close();
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldPassIngoingMessagesToPipeline(@Mock final NodeUpEvent event,
                                                 @Mock final ChannelHandlerContext channelCtx,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture,
                                                 @Mock final ByteBuf message) {
            when(bootstrap.handler(any())).then((Answer<Bootstrap>) invocation -> {
                final SimpleChannelInboundHandler<DatagramPacket> handler = invocation.getArgument(0, SimpleChannelInboundHandler.class);
                handler.channelRead(channelCtx, new DatagramPacket(message, new InetSocketAddress(22527), new InetSocketAddress(25421)));
                return bootstrap;
            });
            when(bootstrap.bind(any(InetAddress.class), anyInt())).thenReturn(channelFuture);
            when(channelFuture.isSuccess()).thenReturn(true);
            when(channelFuture.channel().localAddress()).thenReturn(new InetSocketAddress(22527));
            when(config.getRemoteEndpoints()).thenReturn(Set.of());
            when(message.retain()).thenReturn(message);

            final UdpServer handler = new UdpServer(bootstrap, null);

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler);
            final TestObserver<AddressedByteBuf> inboundMessages = pipeline.inboundMessages(AddressedByteBuf.class).test();

            pipeline.processInbound(event).join();

            inboundMessages.awaitCount(1)
                    .assertValueCount(1);
            pipeline.close();
        }
    }
}
