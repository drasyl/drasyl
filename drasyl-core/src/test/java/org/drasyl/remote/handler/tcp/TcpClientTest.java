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
package org.drasyl.remote.handler.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.remote.handler.tcp.TcpClient.TcpClientHandler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static java.net.InetSocketAddress.createUnresolved;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TcpClientTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Map<SocketAddress, Channel> clientChannels;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Channel serverChannel;
    @Mock
    private Set<InetSocketAddressWrapper> superPeerAddresses;
    @Mock
    private AtomicLong noResponseFromSuperPeerSince;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelFuture superPeerChannel;

    @Nested
    class StopServer {
        @Test
        void shouldStopClientOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
            when(superPeerChannel.isSuccess()).thenReturn(true);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(superPeerChannel.channel()).close();
            }
        }

        @Test
        void shouldStopClientOnNodeDownEvent(@Mock final NodeDownEvent event) {
            when(superPeerChannel.isSuccess()).thenReturn(true);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(superPeerChannel.channel()).close();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPasstroughInboundMessages(@Mock final Address sender, @Mock final Object msg) {
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldStopClientOnInboundMessageFromSuperPeer(@Mock final InetSocketAddressWrapper sender,
                                                           @Mock final ByteBuf msg,
                                                           @Mock final Channel channel) {
            when(superPeerAddresses.contains(any())).thenReturn(true);
            when(superPeerChannel.isSuccess()).thenReturn(true);
            when(superPeerChannel.channel()).thenReturn(channel);

            final AtomicLong noResponseFromSuperPeerSince = new AtomicLong(1337);
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);

                verify(superPeerChannel, timeout(1_000L)).cancel(true);
                verify(superPeerChannel.channel(), timeout(1_000L)).close();
                await().atMost(ofSeconds(1)).untilAsserted(() -> assertEquals(0, noResponseFromSuperPeerSince.get()));
            }
        }

        @Test
        void shouldPasstroughOutboundMessagesWhenNoTcpConnectionIsPresent(@Mock final InetSocketAddressWrapper recipient,
                                                                          @Mock final ByteBuf msg) {
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, msg).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(msg);
            }
        }

        @Test
        void shouldPassOutboundMessagesToTcpConnectionWhenPresent(@Mock final InetSocketAddressWrapper recipient,
                                                                  @Mock final ByteBuf msg,
                                                                  @Mock final ChannelFuture channelFuture) {
            when(superPeerChannel.isSuccess()).thenReturn(true);
            when(superPeerChannel.channel().writeAndFlush(any())).thenReturn(channelFuture);
            when(channelFuture.isDone()).thenReturn(true);
            when(channelFuture.isSuccess()).thenReturn(true);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, msg).join();

                verify(superPeerChannel.channel()).writeAndFlush(msg);
                outboundMessages.assertEmpty();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldStartClientOnOutboundMessageToSuperPeer(@Mock final InetSocketAddressWrapper recipient,
                                                           @Mock final ByteBuf msg,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(superPeerAddresses.contains(any())).thenReturn(true);
            when(bootstrap.handler(any()).connect(any(InetSocketAddress.class))).thenReturn(superPeerChannel);
            when(superPeerChannel.addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return null;
            });
            when(channelFuture.isSuccess()).thenReturn(true);

            final AtomicLong noResponseFromSuperPeerSince = new AtomicLong(1);
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, null);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processOutbound(recipient, msg).join();

                verify(bootstrap.handler(any()), timeout(1_000L)).connect(any(InetSocketAddress.class));
                verify(superPeerChannel, timeout(1_000L)).addListener(any());
            }
        }
    }

    @Nested
    class TcpClientHandlerTest {
        @Mock
        private HandlerContext ctx;

        @Test
        void shouldPassInboundMessageToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            new TcpClientHandler(ctx).channelRead0(nettyCtx, msg);

            verify(ctx).passInbound(any(InetSocketAddressWrapper.class), any(), any());
        }
    }
}
