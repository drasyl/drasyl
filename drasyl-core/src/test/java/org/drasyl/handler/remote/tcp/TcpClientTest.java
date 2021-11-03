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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.channel.InetAddressedMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static java.net.InetSocketAddress.createUnresolved;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TcpClientTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Bootstrap bootstrap;
    @Mock
    private Map<SocketAddress, Channel> clientChannels;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Channel serverChannel;
    @Mock
    private Set<SocketAddress> superPeerAddresses;
    @Mock
    private AtomicLong noResponseFromSuperPeerSince;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelFuture superPeerChannel;
    private final Duration timeout = Duration.ofSeconds(1);
    @Mock
    private InetSocketAddress address;

    @Nested
    class StopServer {
        @Test
        void shouldStopClientOnChannelInactive() {
            when(superPeerChannel.isSuccess()).thenReturn(true);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, timeout, address, superPeerChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelInactive();

                verify(superPeerChannel.channel()).close();
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPassTroughInboundMessages(@Mock final InetSocketAddress sender,
                                             @Mock final Object msg) {
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, timeout, address, superPeerChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, sender));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new InetAddressedMessage<>(msg, sender), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldStopClientOnInboundMessageFromSuperPeer(@Mock final InetSocketAddress sender,
                                                           @Mock final ByteBuf msg) {
            when(superPeerAddresses.contains(any())).thenReturn(true);
            when(superPeerChannel.isSuccess()).thenReturn(true);

            final AtomicLong noResponseFromSuperPeerSince = new AtomicLong(1337);
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, timeout, address, superPeerChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, sender));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new InetAddressedMessage<>(msg, sender), actual);
                verify(superPeerChannel).cancel(true);
                verify(superPeerChannel.channel()).close();
                assertEquals(0, noResponseFromSuperPeerSince.get());

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldPasstroughOutboundMessagesWhenNoTcpConnectionIsPresent(@Mock final InetSocketAddress recipient,
                                                                          @Mock final ByteBuf msg) {
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, timeout, address, superPeerChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new InetAddressedMessage<>(msg, recipient), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldPassOutboundMessagesToTcpConnectionWhenPresent(@Mock final InetSocketAddress recipient,
                                                                  @Mock final ByteBuf msg,
                                                                  @Mock final ChannelFuture channelFuture) {
            when(superPeerChannel.isSuccess()).thenReturn(true);
            when(superPeerChannel.channel().writeAndFlush(any())).thenReturn(channelFuture);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, timeout, address, superPeerChannel);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                verify(superPeerChannel.channel()).write(msg);
                assertNull(channel.readOutbound());
            }
            finally {
                channel.close();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldStartClientOnOutboundMessageToSuperPeer(@Mock final InetSocketAddress recipient,
                                                           @Mock final ByteBuf msg,
                                                           @Mock(answer = RETURNS_DEEP_STUBS) final ChannelFuture channelFuture) {
            when(superPeerAddresses.contains(any())).thenReturn(true);
            when(bootstrap.connect(any(InetSocketAddress.class))).thenReturn(superPeerChannel);
            when(superPeerChannel.addListener(any())).then(invocation -> {
                final ChannelFutureListener listener = invocation.getArgument(0, ChannelFutureListener.class);
                listener.operationComplete(channelFuture);
                return null;
            });
            when(channelFuture.isSuccess()).thenReturn(true);

            final AtomicLong noResponseFromSuperPeerSince = new AtomicLong(1);
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, timeout, address, null);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new InetAddressedMessage<>(msg, recipient));

                verify(bootstrap).connect(any(InetSocketAddress.class));
                verify(superPeerChannel).addListener(any());
            }
            finally {
                channel.releaseOutbound();
                channel.close();
            }
        }
    }

    @Nested
    class TcpClientHandlerTest {
        @Mock
        private ChannelHandlerContext ctx;

        @Test
        void shouldPassInboundMessageToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor eventExecutor) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));
            when(ctx.executor()).thenReturn(eventExecutor);
            doAnswer((Answer<Object>) invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }).when(eventExecutor).execute(any());

            new TcpClient.TcpClientHandler(ctx).channelRead0(nettyCtx, msg);

            verify(ctx).fireChannelRead(any());
        }
    }
}
