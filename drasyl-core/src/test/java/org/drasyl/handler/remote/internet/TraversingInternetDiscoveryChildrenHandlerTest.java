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
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler.SuperPeer;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler.TraversingPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraversingInternetDiscoveryChildrenHandlerTest {
    @Mock
    private IdentityPublicKey myPublicKey;
    @Mock
    private ProofOfWork myProofOfWork;
    @Mock
    private LongSupplier currentTime;

    @Test
    void shouldHandleUniteMessageFromSuperPeer(@Mock final IdentityPublicKey superPeerPublicKey,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                               @Mock final SuperPeer superPeer,
                                               @Mock final InetSocketAddress otherPeerInetAddress,
                                               @Mock final InetSocketAddress superPeerInetAddress) {
        when(uniteMsg.getRecipient()).thenReturn(myPublicKey);
        when(uniteMsg.getSender()).thenReturn(superPeerPublicKey);
        when(uniteMsg.getSocketAddress()).thenReturn(otherPeerInetAddress);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(superPeerPublicKey, superPeer);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null, 60L, 100, traversingPeers);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<DiscoveryMessage> discoveryMsg = channel.readOutbound();
        assertThat(discoveryMsg.content(), instanceOf(DiscoveryMessage.class));
        assertSame(uniteMsg.getSocketAddress(), discoveryMsg.recipient());
        assertTrue(traversingPeers.containsKey(uniteMsg.getAddress()));
    }

    @Test
    void shouldDropUniteMessageNotFromSuperPeer(@Mock final IdentityPublicKey superPeerPublicKey,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                                @Mock final SuperPeer superPeer,
                                                @Mock final InetSocketAddress otherPeerInetAddress,
                                                @Mock final InetSocketAddress superPeerInetAddress) {
        when(uniteMsg.getRecipient()).thenReturn(myPublicKey);
        when(uniteMsg.getSocketAddress()).thenReturn(otherPeerInetAddress);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(superPeerPublicKey, superPeer);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null, 60L, 100, traversingPeers);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void shouldHandleDiscoveryMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final DiscoveryMessage discoveryMsg,
                                                        @Mock final InetSocketAddress inetAddress,
                                                        @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                        @Mock final TraversingPeer traversingPeer) {
        when(discoveryMsg.getRecipient()).thenReturn(myPublicKey);
        when(discoveryMsg.getSender()).thenReturn(traversingPeerPublicKey);
        when(discoveryMsg.getTime()).thenReturn(40L);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<DiscoveryMessage> msg = new InetAddressedMessage<>(discoveryMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null, 60L, 100, traversingPeers);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<AcknowledgementMessage> acknowledgementMsg = channel.readOutbound();
        assertThat(acknowledgementMsg.content(), instanceOf(AcknowledgementMessage.class));
    }

    @Test
    void shouldHandleAcknowledgementMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final AcknowledgementMessage acknowledgementMsg,
                                                              @Mock final InetSocketAddress inetAddress,
                                                              @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                              @Mock final TraversingPeer traversingPeer) {
        when(acknowledgementMsg.getRecipient()).thenReturn(myPublicKey);
        when(acknowledgementMsg.getSender()).thenReturn(traversingPeerPublicKey);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null, 60L, 100, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertThat(channel.readEvent(), instanceOf(AddPathEvent.class));
        verify(traversingPeer).acknowledgementReceived(any());
    }

    @Test
    void shouldRecordApplicationMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                          @Mock final InetSocketAddress inetAddress,
                                                          @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                          @Mock final TraversingPeer traversingPeer) {
        when(applicationMsg.getRecipient()).thenReturn(myPublicKey);
        when(applicationMsg.getSender()).thenReturn(traversingPeerPublicKey);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null, 60L, 100, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        verify(traversingPeer).applicationTrafficSentOrReceived();
    }

    @Test
    void shouldRouteRoutableOutboundMessageAddressedToTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                                     @Mock final IdentityPublicKey publicKey,
                                                                     @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                                     @Mock final TraversingPeer traversingPeer,
                                                                     @Mock final InetSocketAddress traversingPeerInetAddress) {
        when(applicationMsg.getRecipient()).thenReturn(traversingPeerPublicKey);
        when(traversingPeer.inetAddress()).thenReturn(traversingPeerInetAddress);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final OverlayAddressedMessage<ApplicationMessage> msg = new OverlayAddressedMessage<>(applicationMsg, publicKey);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, superPeers, null, null, 60L, 100, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeOutbound(msg);

        final InetAddressedMessage<ApplicationMessage> routedMsg = channel.readOutbound();
        assertSame(applicationMsg, routedMsg.content());
        assertSame(traversingPeerInetAddress, routedMsg.recipient());
        verify(traversingPeer).applicationTrafficSentOrReceived();
    }

    @Nested
    class TraversingPeerTest {
        @Mock
        private LongSupplier currentTime;

        @Nested
        class SetInetAddress {
            @Test
            void shouldSetInetAddress(@Mock final InetSocketAddress inetAddressA,
                                      @Mock final InetSocketAddress inetAddressB) {
                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, inetAddressA, 0L, 0L, 0L);

                traversingPeer.setInetAddress(inetAddressB);

                assertSame(inetAddressB, traversingPeer.inetAddress());
            }
        }

        @Nested
        class DiscoverySent {
            @Test
            void shouldRecordFirstDiscoveryTime(@Mock final InetSocketAddress inetAddress) {
                when(currentTime.getAsLong()).thenReturn(1L).thenReturn(2L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, inetAddress, 0L, 0L, 0L);
                traversingPeer.discoverySent();
                traversingPeer.discoverySent();

                assertEquals(1L, traversingPeer.firstDiscoveryTime);
            }
        }

        @Nested
        class AcknowledgementReceived {
            @Test
            void shouldRecordLastAcknowledgementTimeAndInetAddress(@Mock final InetSocketAddress inetAddressA,
                                                                   @Mock final InetSocketAddress inetAddressB) {
                when(currentTime.getAsLong()).thenReturn(1L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, inetAddressA, 0L, 0L, 0L);
                traversingPeer.acknowledgementReceived(inetAddressB);

                assertEquals(1L, traversingPeer.lastAcknowledgementTime);
                assertSame(inetAddressB, traversingPeer.inetAddress());
            }
        }

        @Nested
        class ApplicationSentOrReceived {
            @Test
            void shouldRecordLastApplicationTime(@Mock final InetSocketAddress inetAddress) {
                when(currentTime.getAsLong()).thenReturn(1L).thenReturn(2L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, inetAddress, 0L, 0L, 0L);
                traversingPeer.applicationTrafficSentOrReceived();

                assertEquals(1L, traversingPeer.lastApplicationTime);
            }
        }

        @Nested
        class IsStale {
        }
    }
}
