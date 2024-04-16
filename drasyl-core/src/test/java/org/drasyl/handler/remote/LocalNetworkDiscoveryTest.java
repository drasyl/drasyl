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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.drasyl.handler.remote.UdpMulticastServer.MULTICAST_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class LocalNetworkDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private Future<?> pingDisposable;

    @Nested
    class EventHandling {
        @BeforeEach
        void setUp() {
            when(identity.getIdentityPublicKey()).thenReturn(ID_1.getIdentityPublicKey());
            when(identity.getAddress()).thenReturn(ID_1.getIdentityPublicKey());
            when(identity.getProofOfWork()).thenReturn(ID_1.getProofOfWork());
        }

        @Test
        void shouldStartHeartbeatingOnChannelActive() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(MULTICAST_ADDRESS, null));
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                verify(handler).startHeartbeat(any());
                handler.stopHeartbeat(); // we must stop, otherwise this handler goes crazy cause to the PT0S ping interval
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldStopHeartbeatingAndClearRoutesOnChannelInactive() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable));
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.pipeline().fireChannelInactive();

                verify(handler).stopHeartbeat();
                verify(handler).clearRoutes(any());
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class StartHeartbeat {
        @Test
        void shouldScheduleHeartbeat(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().config()).thenReturn(config);

            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(MULTICAST_ADDRESS, null));

            handler.startHeartbeat(ctx);

            verify(ctx.executor()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    class DoHeartbeat {
        @Test
        void shouldRemoveStalePeersAndPingLocalNetworkPeers(@Mock final IdentityPublicKey publicKey,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final DrasylChannel channel,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel()).thenReturn(channel);
            when(channel.config()).thenReturn(config);
            when(config.getPeersManager().getPeers(any())).thenReturn(Set.of(publicKey));
            when(config.getPeersManager().isStale(any(), any())).thenReturn(true);

            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable);
            handler.doHeartbeat(ctx);

            verify(config.getPeersManager()).removeClientPath(any(), any(), any());
            verify(ctx).writeAndFlush(argThat((ArgumentMatcher<InetAddressedMessage<?>>) m -> m.content() instanceof HelloMessage && m.recipient().equals(MULTICAST_ADDRESS)));
        }
    }

    @Nested
    class StopHeartbeat {
        @Test
        void shouldStopHeartbeat() {
            final LocalNetworkDiscovery handler = spy(new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable));

            handler.stopHeartbeat();

            verify(pingDisposable).cancel(false);
        }
    }

    @Nested
    class ClearRoutes {
        @Test
        void shouldClearRoutes(@Mock final IdentityPublicKey publicKey,
                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().config()).thenReturn(config);

            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable);
            handler.clearRoutes(ctx);

            verify(config.getPeersManager()).removeClientPaths(any(), any());
        }
    }

    @Nested
    class InboundMessageHandling {
        @Test
        void shouldHandleInboundPingFromOtherNodes(@Mock final InetSocketAddress sender,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().config()).thenReturn(config);
            when(config.getPeersManager().addClientPath(eq(ctx), any(), any(), any(), anyShort())).thenReturn(true);

            final IdentityPublicKey publicKey = ID_2.getIdentityPublicKey();
            final HelloMessage msg = HelloMessage.of(0, publicKey, ID_2.getProofOfWork());

            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable);
            handler.channelRead(ctx, new InetAddressedMessage<>(msg, null, sender));

            verify(config.getPeersManager()).addClientPath(eq(ctx), any(), any(), any(), anyShort());
        }

        @Test
        void shouldIgnoreInboundPingFromItself(@Mock final InetSocketAddress sender,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final IdentityPublicKey publicKey = ID_2.getIdentityPublicKey();
            when(ctx.channel().localAddress()).thenReturn(publicKey);
            when(identity.getIdentityPublicKey()).thenReturn(publicKey);
            when(identity.getAddress()).thenReturn(publicKey);
            final HelloMessage msg = HelloMessage.of(0, publicKey, ID_2.getProofOfWork());
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable);
            handler.channelRead(ctx, new InetAddressedMessage<>(msg, null, sender));

            verify(ctx, never()).fireUserEventTriggered(any(AddPathAndChildrenEvent.class));
        }

        @Test
        void shouldPassThroughUnicastMessages(@Mock final InetSocketAddress sender,
                                              @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage msg) {
            final LocalNetworkDiscovery handler = new LocalNetworkDiscovery(MULTICAST_ADDRESS, pingDisposable);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.pipeline().fireChannelRead(new InetAddressedMessage<>(msg, null, sender));

                final ReferenceCounted actual = channel.readInbound();
                assertEquals(new InetAddressedMessage<>(msg, null, sender), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }
    }
}
