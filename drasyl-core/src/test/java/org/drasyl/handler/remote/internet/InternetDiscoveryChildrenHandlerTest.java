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
package org.drasyl.handler.remote.internet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler.SuperPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternetDiscoveryChildrenHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;
    @Mock
    private LongSupplier currentTime;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;

    @Test
    void shouldContactSuperPeersOnChannelActive(@Mock final IdentityPublicKey superPeerKey,
                                                @Mock final InetSocketAddress superPeerAddress) {
        final Map<IdentityPublicKey, InetSocketAddress> superPeers = Map.of(superPeerKey, superPeerAddress);
        when(config.getSuperPeers()).thenReturn(superPeers);
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(superPeerKey.toByteArray()).thenReturn(new byte[]{ 1, 2, 3 });
        when(identity.getIdentityPublicKey().toByteArray()).thenReturn(new byte[]{ 1, 2, 3 });
        when(identity.getIdentitySecretKey().toByteArray()).thenReturn(new byte[]{ 1, 2, 3 });


        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(currentTime, 0L, null);

        // channel active
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity);
        channel.pipeline().addFirst(handler);
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            final Object msg = channel.readOutbound();
            assertThat(msg, instanceOf(InetAddressedMessage.class));
            assertThat(((InetAddressedMessage<?>) msg).content(), instanceOf(HelloMessage.class));
        });

        // channel inactive
        channel.close();

        assertNull(handler.heartbeatDisposable);
    }

    @Test
    void shouldHandleAcknowledgementMessageFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                         @Mock final IdentityPublicKey superPeerKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress superPeer,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final AcknowledgementMessage acknowledgementMsg,
                                                         @Mock final InetSocketAddress inetAddress,
                                                         @Mock final DrasylAddress myPublicKey) throws Exception {
        final Map<IdentityPublicKey, InetSocketAddress> superPeers = Map.of(superPeerKey, superPeer);
        when(acknowledgementMsg.getSender()).thenReturn(superPeerKey);
        when(acknowledgementMsg.getTime()).thenReturn(1L);
        when(identity.getAddress()).thenReturn(myPublicKey);
        when(acknowledgementMsg.getRecipient()).thenReturn(myPublicKey);
        when(currentTime.getAsLong()).thenReturn(2L);
        when(config.getPeersManager().addSuperPeerPath(any(), any(), any(), any(), anyShort())).thenReturn(true);
        when(config.getSuperPeers()).thenReturn(superPeers);
        when(config.getHelloInterval().toMillis()).thenReturn(5_000L);
        when(config.getMaxMessageAge().toMillis()).thenReturn(10L);
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(currentTime, 0L, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        assertThat(channel.readEvent(), instanceOf(AddPathAndSuperPeerEvent.class));
    }

    @Nested
    class SuperPeerTest {
        @Mock
        private LongSupplier currentTime;
        @Mock
        private InetSocketAddress inetAddress;

        @Nested
        class AcknowledgementReceived {
            @Test
            void shouldRecordLastAcknowledgementTimeAndRtt() {
                when(currentTime.getAsLong()).thenReturn(1L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 0L, inetAddress);
                superPeer.acknowledgementReceived(2L);

                assertEquals(1L, superPeer.lastAcknowledgementTime);
                assertEquals(2L, superPeer.rtt);
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnFalseIfDiscoveryHasJustBeenSent() {
                when(currentTime.getAsLong()).thenReturn(10L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 10L, inetAddress, 0L, 0L);

                assertFalse(superPeer.isStale());
            }

            @Test
            void shouldReturnFalseIfDiscoveryHasNotTimedOut() {
                when(currentTime.getAsLong()).thenReturn(55L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 10L, inetAddress, 50L, 0L);

                assertFalse(superPeer.isStale());
            }

            @Test
            void shouldReturnTrueIfDiscoveryHasBeenTimedOut() {
                when(currentTime.getAsLong()).thenReturn(55L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 10L, inetAddress, 10L, 0L);

                assertTrue(superPeer.isStale());
            }
        }
    }
}
