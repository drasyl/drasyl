/*
 * Copyright (c) 2020.
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
package org.drasyl.remote.handler;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.remote.handler.UdpDiscoveryHandler.Peer;
import org.drasyl.remote.message.AcknowledgementMessage;
import org.drasyl.remote.message.DiscoverMessage;
import org.drasyl.remote.message.MessageId;
import org.drasyl.remote.message.RemoteApplicationMessage;
import org.drasyl.remote.message.RemoteMessage;
import org.drasyl.remote.message.UniteMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdpDiscoveryHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<MessageId, Pair<DiscoverMessage, InetSocketAddressWrapper>> openPingsCache;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Set<CompressedPublicKey> rendezvousPeers;
    @Mock
    private Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    @Mock
    private Map<CompressedPublicKey, Peer> peers;

    @Test
    void shouldStartHeartbeatingOnNodeUpEvent(@Mock final NodeUpEvent event) {
        when(config.getRemotePingInterval()).thenReturn(ofSeconds(5));

        final UdpDiscoveryHandler handler = spy(new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers));
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processInbound(event).join();

        verify(handler).startHeartbeat(any());
    }

    @Test
    void shouldStopHeartbeatingOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
        final UdpDiscoveryHandler handler = spy(new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers));
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processInbound(event).join();

        verify(handler).stopHeartbeat();
    }

    @Test
    void shouldStopHeartbeatingOnNodeDownEvent(@Mock final NodeDownEvent event) {
        final UdpDiscoveryHandler handler = spy(new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers));
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processInbound(event).join();

        verify(handler).stopHeartbeat();
    }

    @Test
    void shouldPassthroughAllOtherEvents(@Mock final NodeEvent event) {
        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Event> inboundEvents = pipeline.inboundEvents().test();

        pipeline.processInbound(event).join();

        inboundEvents.awaitCount(1).assertValueCount(1);
        inboundEvents.assertValue(m -> m instanceof NodeEvent);
    }

    @Nested
    class DoHeartbeat {
        @Test
        void shouldNotRemoveLivingSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                            @Mock final CompressedPublicKey publicKey,
                                            @Mock final InetSocketAddressWrapper address,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peer.getAddress()).thenReturn(address);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>());
            handler.doHeartbeat(ctx);

            verifyNoInteractions(peersManager);
        }

        @Test
        void shouldRemoveDeadSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                       @Mock final CompressedPublicKey publicKey,
                                       @Mock final InetSocketAddressWrapper address,
                                       @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peer.getAddress()).thenReturn(address);
            when(ctx.config().getRemoteSuperPeerEndpoint().getPublicKey()).thenReturn(publicKey);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>());
            handler.doHeartbeat(ctx);

            verify(ctx.peersManager()).unsetSuperPeerAndRemovePath(any());
        }

        @Test
        void shouldRemoveDeadChildrenOrPeers(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                             @Mock final CompressedPublicKey publicKey,
                                             @Mock final InetSocketAddressWrapper address,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peer.getAddress()).thenReturn(address);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>());
            handler.doHeartbeat(ctx);

            verify(ctx.peersManager()).removeChildrenAndPath(eq(publicKey), any());
        }

        @Test
        void shouldPingSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            when(ctx.config().isRemoteSuperPeerEnabled()).thenReturn(true);
            when(ctx.config().getRemoteSuperPeerEndpoint().getHost()).thenReturn("127.0.0.1");

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, new HashSet<>());
            handler.doHeartbeat(ctx);

            verify(ctx).write(any(), any(DiscoverMessage.class), any());
        }

        @Test
        void shouldPingPeersWithRecentCommunication(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                    @Mock final CompressedPublicKey publicKey,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                    @Mock final InetSocketAddressWrapper address) {
            when(peer.hasControlTraffic(any())).thenReturn(true);
            when(peer.hasApplicationTraffic(any())).thenReturn(true);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(Set.of(publicKey)));
            handler.doHeartbeat(ctx);

            verify(ctx).write(any(), any(DiscoverMessage.class), any());
        }

        @Test
        void shouldNotPingPeersWithoutRecentCommunication(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                          @Mock final CompressedPublicKey publicKey,
                                                          @Mock final InetSocketAddressWrapper address) {
            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, new Peer())), new HashSet<>(Set.of(publicKey)));
            handler.doHeartbeat(ctx);

            verify(ctx, never()).write(any(), any(), any());
        }
    }

    @Test
    void shouldReplyWithWelcomeMessageToJoinMessage(@Mock final InetSocketAddressWrapper sender,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final DiscoverMessage message,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(message.getSender(), peer)), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Object> outboundMessages = pipeline.outboundOnlyMessages().test();

        pipeline.processInbound(sender, message);

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(m -> m instanceof AcknowledgementMessage);
        verify(peersManager, never()).setPeerInformationAndAddPath(any(), any(), any());
    }

    @Test
    void shouldUpdatePeerInformationOnWelcomeMessage(@Mock final InetSocketAddressWrapper sender,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final DiscoverMessage discoverMessage,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper address,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final AcknowledgementMessage acknowledgementMessage,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(new HashMap<>(Map.of(acknowledgementMessage.getCorrespondingId(), Pair.of(discoverMessage, address))), uniteAttemptsCache, new HashMap<>(Map.of(discoverMessage.getSender(), peer)), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processInbound(sender, acknowledgementMessage).join();

        verify(peersManager).setPeerInformationAndAddPath(any(), any(), any());
    }

    @Test
    void shouldHandleRendezvousMessageFromSuperPeer(@Mock final InetSocketAddressWrapper sender,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMessage,
                                                    @Mock final CompressedPublicKey publicKey,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
        when(config.getRemoteSuperPeerEndpoint().getPublicKey()).thenReturn(publicKey);
        when(uniteMessage.getSender()).thenReturn(publicKey);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(uniteMessage.getPublicKey(), peer)), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processInbound(sender, uniteMessage).join();

        verify(rendezvousPeers).add(any());
    }

    @Test
    void shouldRelayInboundMessageForKnownRecipient(@Mock final InetSocketAddressWrapper sender,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer) {
        when(recipientPeer.isReachable(any())).thenReturn(true);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(message.getRecipient(), recipientPeer), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

        pipeline.processInbound(sender, message).join();

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(p -> p.first().equals(recipientPeer.getAddress()) && p.second().equals(message));
    }

    @Test
    void shouldInitiateRendezvousForInboundMessageWithKnownSenderAndRecipient(@Mock final InetSocketAddressWrapper sender,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Peer senderPeer,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper senderSocketAddress,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper recipientSocketAddress) {
        when(recipientPeer.isReachable(any())).thenReturn(true);
        when(senderPeer.getAddress()).thenReturn(senderSocketAddress);
        when(recipientPeer.getAddress()).thenReturn(recipientSocketAddress);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(message.getSender(), senderPeer, message.getRecipient(), recipientPeer), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

        pipeline.processInbound(sender, message).join();

        outboundMessages.awaitCount(3).assertValueCount(3);
        outboundMessages.assertValueAt(1, p -> p.first().equals(senderSocketAddress) && p.second() instanceof UniteMessage);
        outboundMessages.assertValueAt(2, p -> p.first().equals(recipientSocketAddress) && p.second() instanceof UniteMessage);
    }

    @Test
    void shouldRelayOutboundMessageToKnowRecipient(@Mock final CompressedPublicKey recipient,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final RemoteApplicationMessage message,
                                                   @Mock final InetSocketAddressWrapper recipientSocketAddress,
                                                   @Mock final Peer recipientPeer) {
        when(recipientPeer.getAddress()).thenReturn(recipientSocketAddress);
        when(recipientPeer.isReachable(any())).thenReturn(true);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(recipient, recipientPeer), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

        pipeline.processOutbound(recipient, message).join();

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(p -> p.first().equals(recipientSocketAddress) && p.second().equals(message));
    }

    @Test
    void shouldRelayOutboundMessageToSuperPeerForUnknownRecipient(@Mock final CompressedPublicKey recipient,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final RemoteApplicationMessage message,
                                                                  @Mock final InetSocketAddressWrapper superPeerSocketAddress,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeerPeer) {
        when(superPeerPeer.getAddress()).thenReturn(superPeerSocketAddress);
        when(peersManager.getSuperPeerKey()).thenReturn(recipient);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(recipient, superPeerPeer), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

        pipeline.processOutbound(recipient, message).join();

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(p -> p.first().equals(superPeerSocketAddress) && p.second().equals(message));
    }

    @Test
    void shouldPassthroughForUnknownRecipientWhenNoSuperPeerIsPresent(@Mock final CompressedPublicKey recipient,
                                                                      @Mock(answer = RETURNS_DEEP_STUBS) final RemoteApplicationMessage message) {
        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

        pipeline.processOutbound(recipient, message).join();

        outboundMessages.awaitCount(1).assertValueCount(1);
        outboundMessages.assertValue(p -> p.first().equals(recipient) && p.second().equals(message));
    }

    @Test
    void shouldUpdateLastCommunicationTimeForInboundApplicationMessages(@Mock final CompressedPublicKey sender,
                                                                        @Mock(answer = RETURNS_DEEP_STUBS) final RemoteApplicationMessage message,
                                                                        @Mock final CompressedPublicKey publicKey,
                                                                        @Mock final Peer peer) {
        when(rendezvousPeers.contains(any())).thenReturn(true);
        when(identity.getPublicKey()).thenReturn(publicKey);
        when(message.getRecipient()).thenReturn(publicKey);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(message.getSender(), peer)), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processInbound(sender, message).join();

        verify(peer).applicationTrafficOccurred();
    }

    @Test
    void shouldUpdateLastCommunicationTimeForOutboundMessages(@Mock final CompressedPublicKey recipient,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final RemoteApplicationMessage message,
                                                              @Mock final Peer peer) {
        when(rendezvousPeers.contains(any())).thenReturn(true);

        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(message.getRecipient(), peer)), rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

        pipeline.processOutbound(recipient, message).join();

        verify(peer).applicationTrafficOccurred();
    }
}