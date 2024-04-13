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

import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.internet.InternetDiscoverySuperPeerHandler.ChildrenPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.RemoteMessage;
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
import java.util.Set;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternetDiscoverySuperPeerHandlerTest {
    @Mock
    private IdentityPublicKey myPublicKey;
    @Mock
    private ProofOfWork myProofOfWork;
    @Mock
    private LongSupplier currentTime;
    @Mock
    private HopCount hopLimit;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;

    @Test
    void shouldCheckForStaleChildrenOnChannelActive(@Mock final IdentityPublicKey publicKey,
                                                    @Mock final ChildrenPeer childrenPeer) {
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKey, childrenPeer));
        when(childrenPeer.isStale()).thenReturn(true);
        when(peersManager.removePath(any(), any())).thenReturn(true);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);

        // channel active
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
        await().untilAsserted(() -> {
            channel.runScheduledPendingTasks();
            final Object evt = channel.readEvent();
            assertThat(evt, instanceOf(RemoveChildrenAndPathEvent.class));
        });
        assertTrue(childrenPeers.isEmpty());

        // channel inactive
        channel.close();

        assertNull(handler.stalePeerCheckDisposable);
    }

    @Test
    void shouldHandleHelloMessageWithChildrenTime(@Mock final IdentityPublicKey publicKey,
                                                  @Mock(answer = RETURNS_DEEP_STUBS) final HelloMessage helloMsg,
                                                  @Mock final InetSocketAddress inetAddress) {
        when(currentTime.getAsLong()).thenReturn(11L);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>();
        when(helloMsg.getTime()).thenReturn(10L);
        when(helloMsg.getSender()).thenReturn(publicKey);
        when(helloMsg.getRecipient()).thenReturn(myPublicKey);
        when(helloMsg.getChildrenTime()).thenReturn(100L);
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);
        when(peersManager.addPath(any(), any(), any(), anyShort())).thenReturn(true);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertThat(channel.readEvent(), instanceOf(AddPathAndChildrenEvent.class));
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
        when(helloMsg.getChildrenTime()).thenReturn(100L);
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

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
        when(helloMsg.getChildrenTime()).thenReturn(100L);
        final InetAddressedMessage<HelloMessage> msg = new InetAddressedMessage<>(helloMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

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
        when(childrenPeer.publicInetAddress()).thenReturn(childrenInetAddress);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKey, childrenPeer));
        when(applicationMsg.getRecipient()).thenReturn(publicKey);
        when(applicationMsg.incrementHopCount()).thenReturn(applicationMsg);
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

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
        when(childrenPeer.publicInetAddress()).thenReturn(childrenInetAddress);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKey, childrenPeer));
        when(applicationMsg.getRecipient()).thenReturn(publicKey);
        when(hopLimit.compareTo(any())).thenReturn(0);
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertNull(channel.readOutbound());
    }

    @Test
    void shouldDropRoutableMessageAddressedToUknownPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                        @Mock final InetSocketAddress inetAddress) {
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = Map.of();
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);

        final InternetDiscoverySuperPeerHandler handler = new InternetDiscoverySuperPeerHandler(currentTime, childrenPeers, hopLimit, null);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        assertNull(channel.readOutbound());
        verify(applicationMsg).release();
    }

    @Nested
    class ChildrenPeerTest {
        @Mock
        private LongSupplier currentTime;
        @Mock
        private InetSocketAddress inetAddress;
        @Mock
        private Set<InetSocketAddress> privateInetAddresses;

        @Nested
        class DiscoveryReceived {
            @Test
            void shouldRecordLatestDiscoveryTime() {
                when(currentTime.getAsLong()).thenReturn(1L);

                final ChildrenPeer childrenPeer = new ChildrenPeer(currentTime, 0L, inetAddress, privateInetAddresses);
                childrenPeer.helloReceived(inetAddress, privateInetAddresses);

                assertEquals(1L, childrenPeer.lastHelloTime);
            }
        }

        @Nested
        class IsStale {
            @Test
            void shouldReturnFalseIfDiscoveryHasNotTimedOut() {
                when(currentTime.getAsLong()).thenReturn(55L);

                final ChildrenPeer childrenPeer = new ChildrenPeer(currentTime, 10L, inetAddress, privateInetAddresses, 50L);

                assertFalse(childrenPeer.isStale());
            }

            @Test
            void shouldReturnTrueIfDiscoveryHasBeenTimedOut() {
                when(currentTime.getAsLong()).thenReturn(55L);

                final ChildrenPeer childrenPeer = new ChildrenPeer(currentTime, 10L, inetAddress, privateInetAddresses, 5L);

                assertTrue(childrenPeer.isStale());
            }
        }
    }
}
