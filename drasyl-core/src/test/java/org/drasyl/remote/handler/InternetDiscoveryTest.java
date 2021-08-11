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
package org.drasyl.remote.handler;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCounted;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.event.NodeEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.remote.handler.InternetDiscovery.Peer;
import org.drasyl.remote.handler.InternetDiscovery.Ping;
import org.drasyl.remote.protocol.AcknowledgementMessage;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.drasyl.remote.protocol.DiscoveryMessage;
import org.drasyl.remote.protocol.Nonce;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.remote.protocol.UniteMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternetDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<Nonce, Ping> openPingsCache;
    @Mock
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Set<IdentityPublicKey> rendezvousPeers;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Set<IdentityPublicKey> superPeers;
    @Mock
    private Map<Pair<IdentityPublicKey, IdentityPublicKey>, Boolean> uniteAttemptsCache;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<IdentityPublicKey, Peer> peers;
    private IdentityPublicKey bestSuperPeer;

    @Test
    void shouldPassthroughAllOtherEvents(@Mock final NodeEvent event) {
        final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, peers, rendezvousPeers, superPeers, bestSuperPeer);
        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
        try {
            pipeline.pipeline().fireUserEventTriggered(event);

            assertThat(pipeline.readUserEvent(), instanceOf(NodeEvent.class));
        }
        finally {
            pipeline.close();
        }
    }

    @Nested
    class DoHeartbeat {
        @Test
        void shouldStartHeartbeatingOnChannelActive() {
            when(config.getRemotePingInterval()).thenReturn(ofSeconds(5));

            final InternetDiscovery handler = spy(new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, peers, rendezvousPeers, superPeers, bestSuperPeer));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelActive();

                verify(handler).startHeartbeat(any());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldStopHeartbeatingOnChannelInactive(@Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey publicKey,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            final HashMap<IdentityPublicKey, Peer> peers = new HashMap<>(Map.of(publicKey, peer));
            final InternetDiscovery handler = spy(new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, peers, rendezvousPeers, superPeers, bestSuperPeer));
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelInactive();

                verify(handler).stopHeartbeat();
                verify(openPingsCache).clear();
                verify(uniteAttemptsCache).clear();
                verify(rendezvousPeers).remove(publicKey);
                assertTrue(peers.isEmpty());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldReplyWithAcknowledgmentMessageToDiscoveryMessage(@Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress address) {
            final IdentityPublicKey sender = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey recipient = IdentityTestUtil.ID_2.getIdentityPublicKey();
            when(identity.getIdentityPublicKey()).thenReturn(recipient);
            final DiscoveryMessage discoveryMessage = DiscoveryMessage.of(0, sender, IdentityTestUtil.ID_1.getProofOfWork(), recipient, System.currentTimeMillis());
            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers, superPeers, bestSuperPeer);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(discoveryMessage, address));

                final AddressedMessage<RemoteMessage, SocketAddress> actual = pipeline.readOutbound();
                assertThat(actual.message(), instanceOf(AcknowledgementMessage.class));
                verify(peersManager, never()).addPath(any(), any(), any());

                actual.release();
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldUpdatePeerInformationOnAcknowledgementMessageFromNormalPeer(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress address,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            final IdentityPublicKey sender = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey recipient = IdentityTestUtil.ID_2.getIdentityPublicKey();
            when(identity.getIdentityPublicKey()).thenReturn(recipient);
            final AcknowledgementMessage acknowledgementMessage = AcknowledgementMessage.of(0, sender, IdentityTestUtil.ID_1.getProofOfWork(), recipient, Nonce.randomNonce());
            final InternetDiscovery handler = new InternetDiscovery(new HashMap<>(Map.of(acknowledgementMessage.getCorrespondingId(), new Ping(address))), identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers, superPeers, bestSuperPeer);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(acknowledgementMessage, address));

                verify(peersManager).addPath(any(), any(), any());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldUpdatePeerInformationOnAcknowledgementMessageFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress address,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                                              @Mock final Endpoint superPeerEndpoint) {
            final IdentityPublicKey sender = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey recipient = IdentityTestUtil.ID_2.getIdentityPublicKey();
            when(peer.getAddress()).thenReturn(new InetSocketAddress(22527));
            when(identity.getIdentityPublicKey()).thenReturn(recipient);
            when(config.getRemoteSuperPeerEndpoints()).thenReturn(ImmutableSet.of(superPeerEndpoint));

            final AcknowledgementMessage acknowledgementMessage = AcknowledgementMessage.of(0, sender, IdentityTestUtil.ID_1.getProofOfWork(), recipient, Nonce.randomNonce());
            final InternetDiscovery handler = new InternetDiscovery(new HashMap<>(Map.of(acknowledgementMessage.getCorrespondingId(), new Ping(address))), identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers, Set.of(sender), bestSuperPeer);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(acknowledgementMessage, address));

                verify(peersManager).addPathAndSuperPeer(any(), any(), any());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldNotRemoveLivingSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                            @Mock final IdentityPublicKey publicKey,
                                            @Mock final InetSocketAddress address,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get()).thenReturn(mock(DrasylConfig.class, RETURNS_DEEP_STUBS));
            when(peer.getAddress()).thenReturn(address);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(), superPeers, bestSuperPeer);
            handler.doHeartbeat(ctx);

            verify(peersManager, never()).removeSuperPeerAndPath(any(), any(), any());
        }

        @Test
        void shouldRemoveDeadSuperPeers(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                        @Mock final IdentityPublicKey publicKey,
                                        @Mock final InetSocketAddress address,
                                        @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                        @Mock final Endpoint superPeerEndpoint) {
            when(peer.getAddress()).thenReturn(address);
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get()).thenReturn(config);
            when(config.getRemoteSuperPeerEndpoints()).thenReturn(ImmutableSet.of(superPeerEndpoint));
            when(superPeers.contains(publicKey)).thenReturn(true);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(), superPeers, bestSuperPeer);
            handler.doHeartbeat(ctx);

            verify(peersManager).removeSuperPeerAndPath(any(), eq(publicKey), any());
        }

        @Test
        void shouldRemoveDeadChildrenOrPeers(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                             @Mock final IdentityPublicKey publicKey,
                                             @Mock final InetSocketAddress address,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get()).thenReturn(mock(DrasylConfig.class, RETURNS_DEEP_STUBS));
            when(peer.getAddress()).thenReturn(address);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(), superPeers, bestSuperPeer);
            handler.doHeartbeat(ctx);

            verify(peersManager).removeChildrenAndPath(any(), eq(publicKey), any());
        }

        @Test
        void shouldPingSuperPeers(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                  @Mock final Endpoint superPeerEndpoint) {
            final IdentityPublicKey myPublicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();

            when(ctx.channel().attr(CONFIG_ATTR_KEY).get()).thenReturn(mock(DrasylConfig.class, RETURNS_DEEP_STUBS));
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get().isRemoteSuperPeerEnabled()).thenReturn(true);
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get().getRemoteSuperPeerEndpoints()).thenReturn(ImmutableSet.of(superPeerEndpoint));
            when(superPeerEndpoint.getHost()).thenReturn("127.0.0.1");
            when(ctx.channel().attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class, RETURNS_DEEP_STUBS));
            when(ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(myPublicKey);
            when(superPeerEndpoint.getIdentityPublicKey()).thenReturn(publicKey);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, peers, new HashSet<>(), superPeers, bestSuperPeer);
            handler.doHeartbeat(ctx);

            verify(ctx).writeAndFlush(argThat((ArgumentMatcher<AddressedMessage>) m -> m.message() instanceof DiscoveryMessage));
        }

        @Test
        void shouldPingPeersWithRecentCommunication(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get()).thenReturn(mock(DrasylConfig.class, RETURNS_DEEP_STUBS));
            final IdentityPublicKey myPublicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();

            when(peer.hasControlTraffic(any())).thenReturn(true);
            when(peer.hasApplicationTraffic(any())).thenReturn(true);
            when(ctx.channel().attr(IDENTITY_ATTR_KEY).get()).thenReturn(mock(Identity.class, RETURNS_DEEP_STUBS));
            when(ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey()).thenReturn(myPublicKey);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(Set.of(publicKey)), superPeers, bestSuperPeer);
            handler.doHeartbeat(ctx);

            verify(ctx).writeAndFlush(argThat((ArgumentMatcher<AddressedMessage>) m -> m.message() instanceof DiscoveryMessage));
        }

        @Test
        void shouldNotPingPeersWithoutRecentCommunication(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(ctx.channel().attr(CONFIG_ATTR_KEY).get()).thenReturn(mock(DrasylConfig.class, RETURNS_DEEP_STUBS));
            final IdentityPublicKey publicKey = IdentityTestUtil.ID_1.getIdentityPublicKey();

            when(peer.hasControlTraffic(any())).thenReturn(true);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(Set.of(publicKey)), superPeers, bestSuperPeer);
            handler.doHeartbeat(ctx);

            verify(ctx, never()).writeAndFlush(any(AddressedMessage.class));
            verify(peersManager).removeChildrenAndPath(any(), eq(publicKey), any());
        }
    }

    @Nested
    class Uniting {
        @Test
        void shouldHandleUniteMessageFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress address,
                                                   @Mock final Endpoint superPeerEndpoint) {
            final IdentityPublicKey sender = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey recipient = IdentityTestUtil.ID_2.getIdentityPublicKey();
            when(config.getRemoteSuperPeerEndpoints()).thenReturn(ImmutableSet.of(superPeerEndpoint));
            when(identity.getIdentityPublicKey()).thenReturn(recipient);
            when(superPeers.contains(sender)).thenReturn(true);

            final UniteMessage uniteMessage = UniteMessage.of(0, sender, IdentityTestUtil.ID_1.getProofOfWork(), recipient, IdentityTestUtil.ID_3.getIdentityPublicKey(), new InetSocketAddress(22527));
            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(uniteMessage.getPublicKey(), peer)), rendezvousPeers, superPeers, bestSuperPeer);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(uniteMessage, address));

                verify(rendezvousPeers).add(any());
            }
            finally {
                pipeline.releaseOutbound();
                pipeline.close();
            }
        }

        @Test
        void shouldInitiateUniteForInboundMessageWithKnownSenderAndRecipient(@Mock final InetSocketAddress sender,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer senderPeer,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer) {
            final InetSocketAddress senderSocketAddress = new InetSocketAddress(80);
            final InetSocketAddress recipientSocketAddress = new InetSocketAddress(81);
            final IdentityPublicKey myKey = IdentityTestUtil.ID_1.getIdentityPublicKey();
            final IdentityPublicKey senderKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
            final IdentityPublicKey recipientKey = IdentityTestUtil.ID_3.getIdentityPublicKey();

            when(recipientPeer.isReachable(any())).thenReturn(true);
            when(senderPeer.getAddress()).thenReturn(senderSocketAddress);
            when(recipientPeer.getAddress()).thenReturn(recipientSocketAddress);
            when(identity.getIdentityPublicKey()).thenReturn(myKey);
            when(message.getSender()).thenReturn(senderKey);
            when(message.getRecipient()).thenReturn(recipientKey);

            final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, Map.of(message.getSender(), senderPeer, message.getRecipient(), recipientPeer), rendezvousPeers, superPeers, bestSuperPeer);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(message, sender));

                final AddressedMessage<RemoteMessage, SocketAddress> actual1 = pipeline.readOutbound();
                assertEquals(message, actual1.message());
                final AddressedMessage<RemoteMessage, SocketAddress> actual2 = pipeline.readOutbound();
                assertThat(actual2.message(), instanceOf(UniteMessage.class));
                final AddressedMessage<RemoteMessage, SocketAddress> actual3 = pipeline.readOutbound();
                assertThat(actual3.message(), instanceOf(UniteMessage.class));

                actual1.release();
                actual2.release();
                actual3.release();
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class ApplicationTrafficRouting {
        @Nested
        class Inbound {
            @Test
            void shouldRelayMessageForKnownRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer) {
                final SocketAddress sender = new InetSocketAddress(22527);
                when(recipientPeer.isReachable(any())).thenReturn(true);
                when(recipientPeer.getAddress()).thenReturn(new InetSocketAddress(25421));

                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, Map.of(message.getRecipient(), recipientPeer), rendezvousPeers, superPeers, bestSuperPeer);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(message, sender));

                    final AddressedMessage<RemoteMessage, SocketAddress> actual = pipeline.readOutbound();
                    assertEquals(message, actual.message());

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            void shouldCompleteExceptionallyOnInvalidMessage(@Mock final InetSocketAddress sender,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey recipient) {
                when(message.getRecipient()).thenThrow(IllegalArgumentException.class);

                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(recipient, recipientPeer)), rendezvousPeers, superPeers, bestSuperPeer);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(message, sender));

                    assertNull(pipeline.readOutbound());
                }
                finally {
                    pipeline.close();
                }
            }

            @SuppressWarnings("SuspiciousMethodCalls")
            @Test
            void shouldUpdateLastCommunicationTimeAndConvertSenderOnMessage(
                    @Mock final Peer peer,
                    @Mock final InetSocketAddress address) {
                final IdentityPublicKey sender = IdentityTestUtil.ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = IdentityTestUtil.ID_2.getIdentityPublicKey();
                when(rendezvousPeers.contains(any())).thenReturn(true);
                when(identity.getIdentityPublicKey()).thenReturn(recipient);

                final ApplicationMessage applicationMessage = ApplicationMessage.of(0, sender, IdentityTestUtil.ID_1.getProofOfWork(), recipient, byte[].class.getName(), ByteString.EMPTY);
                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers, superPeers, bestSuperPeer);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.pipeline().fireChannelRead(new AddressedMessage<>(applicationMessage, address));

                    verify(peer).applicationTrafficOccurred();
                    final ReferenceCounted actual = pipeline.readInbound();
                    assertEquals(new AddressedMessage<>(applicationMessage, sender), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }
        }

        @Nested
        class Outbound {
            @Test
            void shouldRelayMessageToKnowRecipient(@Mock final Peer recipientPeer,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
                final InetSocketAddress recipientSocketAddress = new InetSocketAddress(22527);
                final IdentityPublicKey recipient = IdentityTestUtil.ID_1.getIdentityPublicKey();

                when(recipientPeer.getAddress()).thenReturn(recipientSocketAddress);
                when(recipientPeer.isReachable(any())).thenReturn(true);
                when(identity.getIdentityPublicKey()).thenReturn(recipient);

                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, Map.of(recipient, recipientPeer), rendezvousPeers, superPeers, bestSuperPeer);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

                    final ReferenceCounted actual = pipeline.readOutbound();
                    assertEquals(new AddressedMessage<>(message, recipientSocketAddress), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            void shouldRelayMessageToSuperPeerForUnknownRecipient(@Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeerPeer,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
                final InetSocketAddress superPeerSocketAddress = new InetSocketAddress(22527);
                final IdentityPublicKey recipient = IdentityTestUtil.ID_1.getIdentityPublicKey();

                when(superPeerPeer.getAddress()).thenReturn(superPeerSocketAddress);
                when(identity.getIdentityPublicKey()).thenReturn(recipient);

                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, Map.of(recipient, superPeerPeer), rendezvousPeers, superPeers, recipient);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

                    final ReferenceCounted actual = pipeline.readOutbound();
                    assertEquals(new AddressedMessage<>(message, superPeerSocketAddress), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @Test
            void shouldPassthroughForUnknownRecipientWhenNoSuperPeerIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
                final IdentityPublicKey sender = IdentityTestUtil.ID_1.getIdentityPublicKey();
                final IdentityPublicKey recipient = IdentityTestUtil.ID_2.getIdentityPublicKey();

                when(identity.getIdentityPublicKey()).thenReturn(sender);

                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, peers, rendezvousPeers, superPeers, bestSuperPeer);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

                    final ReferenceCounted actual = pipeline.readOutbound();
                    assertEquals(new AddressedMessage<>(message, recipient), actual);

                    actual.release();
                }
                finally {
                    pipeline.close();
                }
            }

            @SuppressWarnings({ "SuspiciousMethodCalls" })
            @Test
            void shouldUpdateLastCommunicationTimeForApplicationMessages(@Mock final Peer peer,
                                                                         @Mock final ApplicationMessage message) {
                final IdentityPublicKey recipient = IdentityTestUtil.ID_1.getIdentityPublicKey();

                when(rendezvousPeers.contains(any())).thenReturn(true);
                when(identity.getIdentityPublicKey()).thenReturn(recipient);

                final InternetDiscovery handler = new InternetDiscovery(openPingsCache, identity, peersManager, uniteAttemptsCache, new HashMap<>(Map.of(recipient, peer)), rendezvousPeers, superPeers, bestSuperPeer);
                final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
                try {
                    pipeline.writeAndFlush(new AddressedMessage<>(message, recipient));

                    verify(peer).applicationTrafficOccurred();
                }
                finally {
                    pipeline.releaseOutbound();
                    pipeline.close();
                }
            }
        }
    }

    @Nested
    class TestPeer {
        private InetSocketAddress address;

        @BeforeEach
        void setUp() {
            address = new InetSocketAddress(22527);
        }

        @Nested
        class Getter {
            @Test
            void shouldReturnCorrectValues() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis() - 1000;
                final long lastInboundPongTime = System.currentTimeMillis() - 100;
                final long lastApplicationTrafficTime = System.currentTimeMillis();
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                assertEquals(address, peer.getAddress());
                assertEquals(lastInboundControlTrafficTime, peer.getLastInboundControlTrafficTime());
                assertEquals(lastApplicationTrafficTime, peer.getLastApplicationTrafficTime());
            }
        }

        @Nested
        class InboundControlTrafficOccurred {
            @Test
            void shouldUpdateTime() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis() - 1000;
                final long lastInboundPongTime = System.currentTimeMillis();
                final long lastApplicationTrafficTime = System.currentTimeMillis();
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                peer.inboundControlTrafficOccurred();

                assertThat(peer.getLastInboundControlTrafficTime(), greaterThan(lastInboundControlTrafficTime));
            }
        }

        @Nested
        class InboundPongOccurred {
            @Test
            void shouldNotFail() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis();
                final long lastInboundPongTime = System.currentTimeMillis() - 1000;
                final long lastApplicationTrafficTime = System.currentTimeMillis();
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                assertDoesNotThrow(peer::inboundPingOccurred);
            }
        }

        @Nested
        class ApplicationTrafficOccurred {
            @Test
            void shouldUpdateTime() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis();
                final long lastInboundPongTime = System.currentTimeMillis();
                final long lastApplicationTrafficTime = System.currentTimeMillis() - 1000;
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                peer.applicationTrafficOccurred();

                assertThat(peer.getLastApplicationTrafficTime(), greaterThan(lastApplicationTrafficTime));
            }
        }

        @Nested
        class HasApplicationTraffic {
            @Test
            void shouldReturnTrueIfTrafficIsPresent(@Mock final DrasylConfig config) {
                when(config.getRemotePingCommunicationTimeout()).thenReturn(ofSeconds(1));

                final Peer peer = new Peer(address, System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis());

                assertTrue(peer.hasApplicationTraffic(config));
            }
        }

        @Nested
        class HasControlTraffic {
            @Test
            void shouldReturnTrueIfTrafficIsPresent(@Mock final DrasylConfig config) {
                when(config.getRemotePingTimeout()).thenReturn(ofSeconds(1));

                final Peer peer = new Peer(address, System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis());

                assertTrue(peer.hasControlTraffic(config));
            }
        }

        @Nested
        class IsReachable {
            @Test
            void shouldReturnTrueIfPeerIsReachable(@Mock final DrasylConfig config) {
                when(config.getRemotePingTimeout()).thenReturn(ofSeconds(1));

                final Peer peer = new Peer(address, System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis());
                assertTrue(peer.isReachable(config));
            }
        }
    }

    @Nested
    class TestPing {
        private SocketAddress address;

        @BeforeEach
        void setUp() {
            address = new InetSocketAddress(22527);
        }

        @Nested
        class GetAddress {
            @Test
            void shouldReturnAddress() {
                final Ping ping = new Ping(address);

                assertEquals(address, ping.getAddress());
            }
        }

        @Nested
        class Equals {
            @SuppressWarnings("java:S2701")
            @Test
            void shouldRecognizeEqualPairs() {
                final Ping pingA = new Ping(address);
                final Ping pingB = new Ping(address);
                final Ping pingC = new Ping(new InetSocketAddress(25421));

                assertEquals(pingA, pingA);
                assertEquals(pingA, pingB);
                assertEquals(pingB, pingA);
                assertNotEquals(null, pingA);
                assertNotEquals(pingA, pingC);
                assertNotEquals(pingC, pingA);
            }
        }

        @Nested
        class HashCode {
            @Test
            void shouldRecognizeEqualPairs() {
                final Ping pingA = new Ping(address);
                final Ping pingB = new Ping(address);
                final Ping pingC = new Ping(new InetSocketAddress(25421));

                assertEquals(pingA.hashCode(), pingB.hashCode());
                assertNotEquals(pingA.hashCode(), pingC.hashCode());
                assertNotEquals(pingB.hashCode(), pingC.hashCode());
            }
        }

        @Test
        void toStringShouldReturnString() {
            final Ping ping = new Ping(address);

            assertNotNull(ping.toString());
        }
    }
}
