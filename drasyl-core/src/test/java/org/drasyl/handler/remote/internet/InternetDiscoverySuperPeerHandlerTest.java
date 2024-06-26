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
import org.drasyl.handler.remote.internet.InternetDiscoverySuperPeerHandler.ChildrenPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.awaitility.Awaitility.await;
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

@ExtendWith(MockitoExtension.class)
class InternetDiscoverySuperPeerHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;
    @Mock
    private IdentityPublicKey myPublicKey;
    @Mock
    private LongSupplier currentTime;
    @Mock
    private HopCount hopLimit;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;

    @Test
    void shouldCheckForStaleChildrenOnChannelActive(@Mock final IdentityPublicKey publicKey,
                                                    @Mock final ChildrenPeer childrenPeer) {
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKey, childrenPeer));
        when(config.getPeersManager().isStale(any(), any(), any())).thenReturn(true);
        when(config.getPeersManager().removeChildrenPath(any(), any(), any())).thenReturn(true);
        when(config.getHelloInterval().toMillis()).thenReturn(1L);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);

        // channel active
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity);
        channel.pipeline().addFirst(handler);
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            verify(config.getPeersManager()).removeChildrenPath(any(), any(), any());
        });
        assertTrue(childrenPeers.isEmpty());

        // channel inactive
        channel.close();

        assertNull(handler.stalePeerCheckDisposable);
    }

    @Test
    void shouldHandleHelloMessageWithChildrenTime(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                  @Mock final IdentityPublicKey publicKey,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final HelloMessage helloMsg,
                                                  @Mock final InetSocketAddress inetAddress) {
        when(currentTime.getAsLong()).thenReturn(11L);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>();
        when(helloMsg.getTime()).thenReturn(10L);
        when(helloMsg.getSender()).thenReturn(publicKey);
        when(helloMsg.getRecipient()).thenReturn(myPublicKey);
        when(helloMsg.getChildrenTime()).thenReturn(100L);
        when(config.getHelloInterval().toMillis()).thenReturn(2L);
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);
        when(config.getPeersManager().addChildrenPath(any(), any(), any(), any(), anyInt())).thenReturn(true);
        when(identity.getAddress()).thenReturn(myPublicKey);
        when(config.getMaxMessageAge().toMillis()).thenReturn(1L);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        verify(config.getPeersManager()).addChildrenPath(any(), any(), any(), any(), anyInt());
        final InetAddressedMessage<AcknowledgementMessage> replyMsg = channel.readOutbound();
        assertThat(replyMsg.content(), instanceOf(AcknowledgementMessage.class));
        assertSame(replyMsg.recipient(), inetAddress);
        assertTrue(childrenPeers.containsKey(publicKey));
    }

    @Test
    void shouldDropTooOldHelloMessages(@Mock final IdentityPublicKey publicKey,
                                       @Mock(answer = RETURNS_DEEP_STUBS) final HelloMessage helloMsg,
                                       @Mock final InetSocketAddress inetAddress) {
        when(currentTime.getAsLong()).thenReturn(1_000_000L);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>();
        when(helloMsg.getTime()).thenReturn(10L);
        when(helloMsg.getSender()).thenReturn(publicKey);
        when(helloMsg.getRecipient()).thenReturn(myPublicKey);
        when(config.getMaxMessageAge().toMillis()).thenReturn(2L);
        when(config.getHelloInterval().toMillis()).thenReturn(2L);
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void shouldDropTooNewHelloMessages(@Mock final IdentityPublicKey publicKey,
                                       @Mock(answer = RETURNS_DEEP_STUBS) final HelloMessage helloMsg,
                                       @Mock final InetSocketAddress inetAddress) {
        when(currentTime.getAsLong()).thenReturn(10L);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>();
        when(helloMsg.getTime()).thenReturn(1_000_000L);
        when(helloMsg.getSender()).thenReturn(publicKey);
        when(helloMsg.getRecipient()).thenReturn(myPublicKey);
        when(config.getMaxMessageAge().toMillis()).thenReturn(10L);
        when(config.getHelloInterval().toMillis()).thenReturn(1L);
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity);
        channel.pipeline().addFirst(handler);

        channel.writeInbound(msg);

        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
    }

    @Test
    void shouldRelayInboundRoutableMessageAddressedToChildrenPeer(@Mock final IdentityPublicKey publicKey,
                                                                  @Mock final ChildrenPeer childrenPeer,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                                  @Mock final InetSocketAddress inetAddress,
                                                                  @Mock final InetSocketAddress childrenInetAddress) {
        when(config.getPeersManager().resolveInetAddress(any(), any())).thenReturn(childrenInetAddress);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKey, childrenPeer));
        when(applicationMsg.getRecipient()).thenReturn(publicKey);
        when(applicationMsg.incrementHopCount()).thenReturn(applicationMsg);
        when(config.getHelloInterval().toMillis()).thenReturn(2L);
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config);
        channel.pipeline().addLast(handler);

        channel.writeInbound(msg);

        final InetAddressedMessage<RemoteMessage> relayedMsg = channel.readOutbound();
        assertSame(applicationMsg, relayedMsg.content());
        assertSame(childrenInetAddress, relayedMsg.recipient());
        verify(applicationMsg).incrementHopCount();
    }

    @Test
    void shouldDropInboundRoutableMessageAddressedToChildrenPeerExceedingHopLimit(@Mock final IdentityPublicKey publicKey,
                                                                                  @Mock final ChildrenPeer childrenPeer,
                                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                                                  @Mock final InetSocketAddress inetAddress,
                                                                                  @Mock final InetSocketAddress childrenInetAddress) {
        when(config.getPeersManager().resolveInetAddress(any(), any())).thenReturn(childrenInetAddress);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKey, childrenPeer));
        when(applicationMsg.getRecipient()).thenReturn(publicKey);
        when(hopLimit.compareTo(any())).thenReturn(0);
        when(config.getHelloInterval().toMillis()).thenReturn(2L);
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config);
        channel.pipeline().addLast(handler);

        channel.writeInbound(msg);

        assertNull(channel.readOutbound());
    }

    @Test
    void shouldDropRoutableMessageAddressedToUknownPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                        @Mock final InetSocketAddress inetAddress) {
        when(config.getHelloInterval().toMillis()).thenReturn(2L);

        final Map<DrasylAddress, ChildrenPeer> childrenPeers = Map.of();
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config);
        channel.pipeline().addLast(handler);

        channel.writeInbound(msg);

        assertNull(channel.readOutbound());
        verify(applicationMsg).release();
    }
}
