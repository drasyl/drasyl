/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler.SuperPeer;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler.TraversingPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity myIdentity;
    @Mock
    private LongSupplier currentTime;
    @Mock
    private ChannelHandlerContext ctx;

    @Test
    void shouldHandleUniteMessageFromSuperPeer(@Mock final IdentityPublicKey superPeerPublicKey,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                               @Mock final SuperPeer superPeer,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress myPublicKey,
                                               @Mock final InetSocketAddress otherPeerInetAddress,
                                               @Mock final InetSocketAddress superPeerInetAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(uniteMsg.getSender()).thenReturn(superPeerPublicKey);
        when(uniteMsg.getEndpoints()).thenReturn(Set.of(otherPeerInetAddress));
        when(myIdentity.getAddress()).thenReturn(myPublicKey);
        when(uniteMsg.getRecipient()).thenReturn(myPublicKey);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(superPeerPublicKey, superPeer);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, superPeers, null, null, traversingPeers);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<HelloMessage> helloMsg = channel.readOutbound();
        assertThat(helloMsg.content(), instanceOf(HelloMessage.class));
        assertSame(otherPeerInetAddress, helloMsg.recipient());
        assertTrue(traversingPeers.containsKey(uniteMsg.getAddress()));
    }

    @Test
    void shouldDropUniteMessageNotFromSuperPeer(@Mock final IdentityPublicKey superPeerPublicKey,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                                @Mock final SuperPeer superPeer,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress myPublicKey,
                                                @Mock final InetSocketAddress otherPeerInetAddress,
                                                @Mock final InetSocketAddress superPeerInetAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(myIdentity.getAddress()).thenReturn(myPublicKey);
        when(uniteMsg.getRecipient()).thenReturn(myPublicKey);
        when(uniteMsg.getEndpoints()).thenReturn(Set.of(otherPeerInetAddress));
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(superPeerPublicKey, superPeer);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, superPeers, null, null, traversingPeers);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void shouldHandleHelloMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HelloMessage helloMsg,
                                                    @Mock final InetSocketAddress inetAddress,
                                                    @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                    @Mock final TraversingPeer traversingPeer,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress myPublicKey) {
        when(helloMsg.getSender()).thenReturn(traversingPeerPublicKey);
        when(helloMsg.getTime()).thenReturn(5L);
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(config.getMaxMessageAge().toMillis()).thenReturn(10L);
        when(myIdentity.getAddress()).thenReturn(myPublicKey);
        when(helloMsg.getRecipient()).thenReturn(myPublicKey);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, superPeers, null, null, traversingPeers);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<AcknowledgementMessage> acknowledgementMsg = channel.readOutbound();
        assertThat(acknowledgementMsg.content(), instanceOf(AcknowledgementMessage.class));
    }

    @Test
    void shouldHandleAcknowledgementMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final AcknowledgementMessage acknowledgementMsg,
                                                              @Mock final InetSocketAddress inetAddress,
                                                              @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                              @Mock final TraversingPeer traversingPeer,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress myPublicKey) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(myIdentity.getAddress()).thenReturn(myPublicKey);
        when(acknowledgementMsg.getRecipient()).thenReturn(myPublicKey);
        when(acknowledgementMsg.getSender()).thenReturn(traversingPeerPublicKey);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, superPeers, null, null, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        assertThat(channel.readEvent(), instanceOf(AddPathEvent.class));
        verify(traversingPeer).acknowledgementReceived(any());
    }

    @Test
    void shouldRecordApplicationMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                          @Mock final InetSocketAddress inetAddress,
                                                          @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                          @Mock final TraversingPeer traversingPeer,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final DrasylAddress myPublicKey) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(myIdentity.getAddress()).thenReturn(myPublicKey);
        when(applicationMsg.getRecipient()).thenReturn(myPublicKey);
        when(applicationMsg.getSender()).thenReturn(traversingPeerPublicKey);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, superPeers, null, null, traversingPeers);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        verify(traversingPeer).applicationTrafficSentOrReceived();
    }

    @Test
    void shouldRouteRoutableOutboundMessageAddressedToTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                                     @Mock final IdentityPublicKey publicKey,
                                                                     @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                                     @Mock final TraversingPeer traversingPeer,
                                                                     @Mock final InetSocketAddress traversingPeerInetAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(applicationMsg.getRecipient()).thenReturn(traversingPeerPublicKey);
        when(traversingPeer.primaryAddress()).thenReturn(traversingPeerInetAddress);
        when(traversingPeer.isReachable(any())).thenReturn(true);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final OverlayAddressedMessage<ApplicationMessage> msg = new OverlayAddressedMessage<>(applicationMsg, publicKey);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, superPeers, null, null, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

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
        class AddInetAddressCandidate {
            @Test
            void shouldAddInetAddress() {
                final InetSocketAddress inetAddressA = InetSocketAddress.createUnresolved("example.com", 35432);
                final InetSocketAddress inetAddressB = InetSocketAddress.createUnresolved("example.com", 23485);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, new HashSet<>(Set.of(inetAddressA)), 0L, 0L, 0L);

                assertTrue(traversingPeer.addInetAddressCandidate(inetAddressB));
                assertTrue(traversingPeer.inetAddressCandidates().contains(inetAddressB));
            }
        }

        @Nested
        class DiscoverySent {
            @Test
            void shouldRecordFirstDiscoveryTime(@Mock final InetSocketAddress inetAddress) {
                when(currentTime.getAsLong()).thenReturn(1L).thenReturn(2L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, Set.of(inetAddress), 0L, 0L, 0L);
                traversingPeer.helloSent();
                traversingPeer.helloSent();

                assertEquals(1L, traversingPeer.firstHelloTime);
            }
        }

        @Nested
        class AcknowledgementReceived {
            @Test
            void shouldRecordLastAcknowledgementTimeAndInetAddress() {
                final InetSocketAddress inetAddressA = InetSocketAddress.createUnresolved("example.com", 35432);
                final InetSocketAddress inetAddressB = InetSocketAddress.createUnresolved("example.com", 23485);
                when(currentTime.getAsLong()).thenReturn(1L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, new HashSet<>(Set.of(inetAddressA)), 0L, 0L, 0L);
                traversingPeer.acknowledgementReceived(inetAddressB);

                assertEquals(1L, traversingPeer.lastAcknowledgementTime);
                assertSame(inetAddressB, traversingPeer.primaryAddress());
            }
        }

        @Nested
        class ApplicationSentOrReceived {
            @Test
            void shouldRecordLastApplicationTime(@Mock final InetSocketAddress inetAddress) {
                when(currentTime.getAsLong()).thenReturn(1L).thenReturn(2L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, Set.of(inetAddress), 0L, 0L, 0L);
                traversingPeer.applicationTrafficSentOrReceived();

                assertEquals(1L, traversingPeer.lastApplicationTime);
            }
        }

        @Nested
        class IsStale {
        }
    }
}
