/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.JavaDrasylServerChannelConfig;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.remote.internet.TraversingInternetDiscoveryChildrenHandler.TraversingPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class TraversingInternetDiscoveryChildrenHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private JavaDrasylServerChannelConfig config;
    private Identity myIdentity = ID_1;
    @Mock
    private LongSupplier currentTime;

    @Test
    void shouldHandleUniteMessageFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                               @Mock final InetSocketAddress otherPeerInetAddress,
                                               @Mock final InetSocketAddress superPeerInetAddress,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress superPeerAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(config.getSuperPeers()).thenReturn(Map.of(ID_2.getIdentityPublicKey(), superPeerAddress));
        when(uniteMsg.getRecipient()).thenReturn(myIdentity.getIdentityPublicKey());
        when(uniteMsg.getSender()).thenReturn(ID_2.getIdentityPublicKey());
        when(uniteMsg.getEndpoints()).thenReturn(Set.of(otherPeerInetAddress));
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, null, traversingPeers);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<HelloMessage> helloMsg = channel.readOutbound();
        assertThat(helloMsg.content(), instanceOf(HelloMessage.class));
        assertSame(otherPeerInetAddress, helloMsg.recipient());
        assertTrue(traversingPeers.containsKey(uniteMsg.getAddress()));
    }

    @Test
    void shouldDropUniteMessageNotFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final UniteMessage uniteMsg,
                                                @Mock final InetSocketAddress otherPeerInetAddress,
                                                @Mock final InetSocketAddress superPeerInetAddress,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress superPeerAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(config.getSuperPeers()).thenReturn(Map.of(ID_2.getIdentityPublicKey(), superPeerAddress));
        when(uniteMsg.getRecipient()).thenReturn(myIdentity.getIdentityPublicKey());
        when(uniteMsg.getEndpoints()).thenReturn(Set.of(otherPeerInetAddress));
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>();
        final InetAddressedMessage<UniteMessage> msg = new InetAddressedMessage<>(uniteMsg, null, superPeerInetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 100L, null, traversingPeers);
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
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress superPeerAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(config.getSuperPeers()).thenReturn(Map.of(ID_2.getIdentityPublicKey(), superPeerAddress));
        when(config.getMaxMessageAge().toMillis()).thenReturn(10L);
        when(helloMsg.getRecipient()).thenReturn(myIdentity.getIdentityPublicKey());
        when(helloMsg.getSender()).thenReturn(traversingPeerPublicKey);
        when(helloMsg.getTime()).thenReturn(5L);
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, null, traversingPeers);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<AcknowledgementMessage> acknowledgementMsg = channel.readOutbound();
        assertThat(acknowledgementMsg.content(), instanceOf(AcknowledgementMessage.class));
    }

    @Test
    void shouldHandleAcknowledgementMessageFromTraversingPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final AcknowledgementMessage acknowledgementMsg,
                                                              @Mock final InetSocketAddress inetAddress,
                                                              @Mock final IdentityPublicKey traversingPeerPublicKey,
                                                              @Mock final TraversingPeer traversingPeer,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final IdentityPublicKey superPeerKey,
                                                              @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddress superPeerAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        when(config.getSuperPeers()).thenReturn(Map.of(ID_2.getIdentityPublicKey(), superPeerAddress));
        when(config.getMaxMessageAge().toMillis()).thenReturn(10L);
        when(acknowledgementMsg.getRecipient()).thenReturn(myIdentity.getIdentityPublicKey());
        when(acknowledgementMsg.getSender()).thenReturn(traversingPeerPublicKey);
        when(config.getPeersManager().addChildrenPath(any(), any(), any(), any(), anyInt())).thenReturn(true);
        final Map<DrasylAddress, TraversingPeer> traversingPeers = new HashMap<>(Map.of(traversingPeerPublicKey, traversingPeer));
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final TraversingInternetDiscoveryChildrenHandler handler = new TraversingInternetDiscoveryChildrenHandler(currentTime, 0L, null, traversingPeers);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, myIdentity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        verify(config.getPeersManager()).addChildrenPath(any(), any(), any(), any(), anyInt());
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

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, new HashSet<>(Set.of(inetAddressA)));

                assertTrue(traversingPeer.addInetAddressCandidate(inetAddressB));
                assertTrue(traversingPeer.inetAddressCandidates().contains(inetAddressB));
            }
        }

        @Nested
        class AcknowledgementReceived {
            @Test
            void shouldRecordLastAcknowledgementTimeAndInetAddress() {
                final InetSocketAddress inetAddressA = InetSocketAddress.createUnresolved("example.com", 35432);
                final InetSocketAddress inetAddressB = InetSocketAddress.createUnresolved("example.com", 23485);

                final TraversingPeer traversingPeer = new TraversingPeer(currentTime, new HashSet<>(Set.of(inetAddressA)));
                traversingPeer.acknowledgementReceived(inetAddressB);

                assertSame(inetAddressB, traversingPeer.primaryAddress());
            }
        }
    }
}
