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

import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler.SuperPeer;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler.TraversingPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.ProofOfWork;
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
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraversingInternetDiscoveryChildrenHandlerTest {
    @Mock
    private IdentityPublicKey myPublicKey;
    @Mock
    private IdentitySecretKey mySecretKey;
    @Mock
    private ProofOfWork myProofOfWork;
    @Mock
    private LongSupplier currentTime;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;

    @Test
    void shouldHandleUniteMessageFromSuperPeer(@Mock final IdentityPublicKey superPeerPublicKey,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                               @Mock final SuperPeer superPeer,
                                               @Mock final InetSocketAddress otherPeerInetAddress,
                                               @Mock final InetSocketAddress superPeerInetAddress) {
        when(uniteMsg.getRecipient()).thenReturn(myPublicKey);
        when(uniteMsg.getSender()).thenReturn(superPeerPublicKey);
        when(uniteMsg.getEndpoints()).thenReturn(Set.of(otherPeerInetAddress));
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(superPeerPublicKey, superPeer);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, mySecretKey, myProofOfWork, currentTime, peersManager, 0L, 5L, 30L, 60L, superPeers, null, 60L, 100, traversingPeers);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

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
                                                @Mock final InetSocketAddress otherPeerInetAddress,
                                                @Mock final InetSocketAddress superPeerInetAddress) {
        when(uniteMsg.getRecipient()).thenReturn(myPublicKey);
        when(uniteMsg.getEndpoints()).thenReturn(Set.of(otherPeerInetAddress));
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(superPeerPublicKey, superPeer);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, mySecretKey, myProofOfWork, currentTime, peersManager, 0L, 5L, 30L, 60L, superPeers, null, 60L, 100, traversingPeers);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void shouldHandleHelloMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HelloMessage helloMsg,
                                                    @Mock final InetSocketAddress inetAddress,
                                                    @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                    @Mock final TraversingPeer traversingPeer) {
        when(helloMsg.getRecipient()).thenReturn(myPublicKey);
        when(helloMsg.getSender()).thenReturn(traversingPeerPublicKey);
        when(helloMsg.getTime()).thenReturn(40L);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, mySecretKey, myProofOfWork, currentTime, peersManager, 0L, 5L, 30L, 60L, superPeers, null, 60L, 100, traversingPeers);
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
        when(peersManager.addPath(any(), any(), any(), anyShort())).thenReturn(true);
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of();
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(0, myPublicKey, mySecretKey, myProofOfWork, currentTime, peersManager, 0L, 5L, 30L, 60L, superPeers, null, 60L, 100, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertThat(channel.readEvent(), instanceOf(AddPathEvent.class));
        verify(traversingPeer).acknowledgementReceived(any());
    }

    @Nested
    class TraversingPeerTest {
        @Mock
        private LongSupplier currentTime;
        @Mock
        private DrasylAddress address;

        @Nested
        class AddInetAddressCandidate {
            @Test
            void shouldAddInetAddress() {
                final InetSocketAddress inetAddressA = InetSocketAddress.createUnresolved("example.com", 35432);
                final InetSocketAddress inetAddressB = InetSocketAddress.createUnresolved("example.com", 23485);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, new HashSet<>(Set.of(inetAddressA)), 0L, 0L, address, peersManager);

                assertTrue(traversingPeer.addInetAddressCandidate(inetAddressB));
                assertTrue(traversingPeer.inetAddressCandidates().contains(inetAddressB));
            }
        }

        @Nested
        class DiscoverySent {
            @Test
            void shouldRecordFirstDiscoveryTime(@Mock final InetSocketAddress inetAddress) {
                when(currentTime.getAsLong()).thenReturn(1L).thenReturn(2L);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, Set.of(inetAddress), 0L, 0L, address, peersManager);
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

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, 30L, 60L, new HashSet<>(Set.of(inetAddressA)), 0L, 0L, address, peersManager);
                traversingPeer.acknowledgementReceived(inetAddressB);

                assertEquals(1L, traversingPeer.lastAcknowledgementTime);
                assertSame(inetAddressB, traversingPeer.primaryAddress());
            }
        }

        @Nested
        class IsStale {
        }
    }
}
