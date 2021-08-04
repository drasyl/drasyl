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
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DefaultEmbeddedPipeline;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import test.util.IdentityTestUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;

import static org.drasyl.remote.handler.UdpServer.determineActualEndpoints;
import static org.drasyl.util.network.NetworkUtil.getAddresses;
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
            when(config.getRemoteEndpoints()).thenReturn(ImmutableSet.of(Endpoint.of("udp://localhost:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey() + "")));

            final UdpServer handler = new UdpServer(bootstrap, null);
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(bootstrap.handler(any())).bind(any(InetAddress.class), anyInt());
            }
        }

        @Nested
        class DetermineActualEndpoints {
            @Test
            void shouldReturnConfigEndpointsIfSpecified() {
                when(config.getRemoteEndpoints()).thenReturn(ImmutableSet.of(Endpoint.of("udp://foo.bar:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())));

                assertEquals(
                        Set.of(Endpoint.of("udp://foo.bar:22527?publicKey=" + IdentityTestUtil.ID_1.getIdentityPublicKey())),
                        determineActualEndpoints(identity, config, new InetSocketAddress(22527))
                );
            }

            @Test
            void shouldReturnEndpointForSpecificAddressesIfServerIsBoundToSpecificInterfaces() {
                when(identity.getIdentityPublicKey()).thenReturn(IdentityTestUtil.ID_1.getIdentityPublicKey());

                final InetAddress firstAddress = getAddresses().iterator().next();
                if (firstAddress != null) {
                    when(config.getRemoteEndpoints().isEmpty()).thenReturn(true);

                    assertEquals(
                            Set.of(Endpoint.of(firstAddress.getHostAddress(), 22527, IdentityTestUtil.ID_1.getIdentityPublicKey())),
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
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(channel).close();
            }
        }

        @Test
        void shouldStopServerOnNodeDownEvent(@Mock final NodeDownEvent event) {
            when(channel.localAddress()).thenReturn(new InetSocketAddress(22527));

            final UdpServer handler = new UdpServer(bootstrap, channel);
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(channel).close();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPassOutgoingMessagesToUdp(@Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            final InetSocketAddressWrapper recipient = new InetSocketAddressWrapper(1234);
            when(channel.isWritable()).thenReturn(true);
            when(channel.writeAndFlush(any()).isDone()).thenReturn(true);
            when(channel.writeAndFlush(any()).isSuccess()).thenReturn(true);

            final UdpServer handler = new UdpServer(bootstrap, channel);
            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processOutbound(recipient, msg).join();

                verify(channel, times(3)).writeAndFlush(any());
            }
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
            when(config.getRemoteEndpoints()).thenReturn(ImmutableSet.of());
            when(message.retain()).thenReturn(message);

            final UdpServer handler = new UdpServer(bootstrap, null);

            try (final EmbeddedPipeline pipeline = new DefaultEmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<ByteBuf> inboundMessages = pipeline.inboundMessages(ByteBuf.class).test();

                pipeline.processInbound(event).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
        }
    }
}
