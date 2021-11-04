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

import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler.SuperPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternetDiscoveryChildrenHandlerTest {
    @Mock
    private IdentityPublicKey myPublicKey;
    @Mock
    private ProofOfWork myProofOfWork;
    @Mock
    private LongSupplier currentTime;

    @Test
    void shouldContactSuperPeersOnChannelActive(@Mock final IdentityPublicKey publicKey,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(publicKey, superPeer);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null);

        // channel active
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            final Object msg = channel.readOutbound();
            assertThat(msg, instanceOf(InetAddressedMessage.class));
            assertThat(((InetAddressedMessage<?>) msg).content(), instanceOf(DiscoveryMessage.class));
        });

        // channel inactive
        channel.close();

        assertNull(handler.heartbeatDisposable);
    }

    @Test
    void shouldHandleAcknowledgementMessageFromSuperPeer(@Mock final IdentityPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer,
                                                         @Mock final AcknowledgementMessage acknowledgementMsg,
                                                         @Mock final InetSocketAddress inetAddress) {
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(publicKey, superPeer);
        when(acknowledgementMsg.getSender()).thenReturn(publicKey);
        when(acknowledgementMsg.getRecipient()).thenReturn(myPublicKey);
        when(acknowledgementMsg.getTime()).thenReturn(1L);
        when(currentTime.getAsLong()).thenReturn(2L);
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        verify(superPeer).acknowledgementReceived(anyLong());
        assertThat(channel.readEvent(), instanceOf(AddPathAndSuperPeerEvent.class));
    }

    @Test
    void shouldRouteOutboundApplicationMessageAddressedToSuperPeerToSuperPeer(@Mock final IdentityPublicKey publicKey,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer,
                                                                              @Mock final ApplicationMessage applicationMsg,
                                                                              @Mock final InetSocketAddress inetAddress) {
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(publicKey, superPeer);
        final OverlayAddressedMessage<ApplicationMessage> msg = new OverlayAddressedMessage<>(applicationMsg, myPublicKey);
        when(superPeer.inetAddress()).thenReturn(inetAddress);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, publicKey);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeOutbound(msg);

        final InetAddressedMessage<ApplicationMessage> routedMsg = channel.readOutbound();
        assertSame(inetAddress, routedMsg.recipient());
    }

    @Test
    void shouldRouteOutboundApplicationMessageAddressedToUnknownPeerToSuperPeer(@Mock final IdentityPublicKey publicKey,
                                                                                @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer,
                                                                                @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                                                @Mock final InetSocketAddress inetAddress) {
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(publicKey, superPeer);
        final OverlayAddressedMessage<ApplicationMessage> msg = new OverlayAddressedMessage<>(applicationMsg, myPublicKey);
        when(superPeer.inetAddress()).thenReturn(inetAddress);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, publicKey);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeOutbound(msg);

        final InetAddressedMessage<ApplicationMessage> routedMsg = channel.readOutbound();
        assertSame(inetAddress, routedMsg.recipient());
    }

    @Nested
    class SuperPeerTest {
        @Mock
        private LongSupplier currentTime;
        @Mock
        private InetSocketAddress inetAddress;

        @Nested
        class DiscoverySent {
            @Test
            void shouldRecordFirstDiscoveryTime() {
                when(currentTime.getAsLong()).thenReturn(1L).thenReturn(2L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 0L, inetAddress);
                superPeer.discoverySent();
                superPeer.discoverySent();

                assertEquals(1L, superPeer.firstDiscoveryTime);
            }
        }

        @Nested
        class AcknowledgementReceived {
            @Test
            void shouldRecordLastAcknowledgementTimeAndLatency() {
                when(currentTime.getAsLong()).thenReturn(1L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 0L, inetAddress);
                superPeer.acknowledgementReceived(2L);

                assertEquals(1L, superPeer.lastAcknowledgementTime);
                assertEquals(2L, superPeer.latency);
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnFalseIfDiscoveryHasJustBeenSent() {
                when(currentTime.getAsLong()).thenReturn(10L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 10L, inetAddress, 5L, 0L, 0L);

                assertFalse(superPeer.isStale());
            }

            @Test
            void shouldReturnFalseIfDiscoveryHasNotTimedOut() {
                when(currentTime.getAsLong()).thenReturn(55L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 10L, inetAddress, 5L, 50L, 0L);

                assertFalse(superPeer.isStale());
            }

            @Test
            void shouldReturnTrueIfDiscoveryHasBeenTimedOut() {
                when(currentTime.getAsLong()).thenReturn(55L);

                final SuperPeer superPeer = new SuperPeer(currentTime, 10L, inetAddress, 5L, 10L, 0L);

                assertTrue(superPeer.isStale());
            }
        }
    }
}
