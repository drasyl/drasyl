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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
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
        void shouldStopClientOnChannelInactive() {
            when(superPeerChannel.isSuccess()).thenReturn(true);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelInactive();

                verify(superPeerChannel.channel()).close();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPasstroughInboundMessages(@Mock final Address sender, @Mock final Object msg) {
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, sender));

                assertEquals(new AddressedMessage<>(msg, sender), pipeline.readInbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldStopClientOnInboundMessageFromSuperPeer(@Mock final InetSocketAddressWrapper sender,
                                                           @Mock final ByteBuf msg) {
            when(superPeerAddresses.contains(any())).thenReturn(true);
            when(superPeerChannel.isSuccess()).thenReturn(true);

            final AtomicLong noResponseFromSuperPeerSince = new AtomicLong(1337);
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, sender));

                assertEquals(new AddressedMessage<>(msg, sender), pipeline.readInbound());
                verify(superPeerChannel).cancel(true);
                verify(superPeerChannel.channel()).close();
                assertEquals(0, noResponseFromSuperPeerSince.get());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPasstroughOutboundMessagesWhenNoTcpConnectionIsPresent(@Mock final InetSocketAddressWrapper recipient,
                                                                          @Mock final ByteBuf msg) {
            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.writeAndFlush(new AddressedMessage<>(msg, recipient));

                assertEquals(new AddressedMessage<>(msg, recipient), pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassOutboundMessagesToTcpConnectionWhenPresent(@Mock final InetSocketAddressWrapper recipient,
                                                                  @Mock final ByteBuf msg,
                                                                  @Mock final ChannelFuture channelFuture) {
            when(superPeerChannel.isSuccess()).thenReturn(true);
            when(superPeerChannel.channel().writeAndFlush(any())).thenReturn(channelFuture);

            final TcpClient handler = new TcpClient(superPeerAddresses, bootstrap, noResponseFromSuperPeerSince, superPeerChannel);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.writeAndFlush(new AddressedMessage<>((Object) msg, (Address) recipient));

                verify(superPeerChannel.channel()).writeAndFlush(msg);
                assertNull(pipeline.readOutbound());
            }
            finally {
                pipeline.drasylClose();
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
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.writeAndFlush(new AddressedMessage<>((Object) msg, (Address) recipient));

                verify(bootstrap.handler(any())).connect(any(InetSocketAddress.class));
                verify(superPeerChannel).addListener(any());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class TcpClientHandlerTest {
        @Mock
        private ChannelHandlerContext ctx;

        @Test
        void shouldPassInboundMessageToPipeline(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext nettyCtx,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final ByteBuf msg) {
            when(nettyCtx.channel().remoteAddress()).thenReturn(createUnresolved("127.0.0.1", 12345));

            new TcpClientHandler(ctx).channelRead0(nettyCtx, msg);

            verify(ctx).fireChannelRead(any());
        }
    }
}
