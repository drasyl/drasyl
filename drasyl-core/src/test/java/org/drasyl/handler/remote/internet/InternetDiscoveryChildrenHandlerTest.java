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
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.internet.InternetDiscoveryChildrenHandler.SuperPeer;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternetDiscoveryChildrenHandlerTest {
    @Mock
    private Identity myIdentity;
    @Mock
    private LongSupplier currentTime;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;

    @Test
    void shouldContactSuperPeersOnChannelActive(@Mock final IdentityPublicKey publicKey,
                                                @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer) {
        when(publicKey.toByteArray()).thenReturn(new byte[]{ 1, 2, 3 });
        when(myIdentity.getIdentityPublicKey().toByteArray()).thenReturn(new byte[]{ 1, 2, 3 });
        when(myIdentity.getIdentitySecretKey().toByteArray()).thenReturn(new byte[]{ 1, 2, 3 });

        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(publicKey, superPeer);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(null, myIdentity, currentTime, 0L, 5L, 30L, 60L, superPeers, null, peersManager);

        // channel active
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
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
    void shouldHandleAcknowledgementMessageFromSuperPeer(@Mock final IdentityPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final SuperPeer superPeer,
                                                         @Mock final AcknowledgementMessage acknowledgementMsg,
                                                         @Mock final InetSocketAddress inetAddress) throws Exception {
        final Map<IdentityPublicKey, SuperPeer> superPeers = Map.of(publicKey, superPeer);
        when(acknowledgementMsg.getSender()).thenReturn(publicKey);
        when(acknowledgementMsg.getRecipient()).thenReturn(myIdentity.getIdentityPublicKey());
        when(acknowledgementMsg.getTime()).thenReturn(1L);
        when(currentTime.getAsLong()).thenReturn(2L);
        when(peersManager.addPath(any(), any(), any(), anyShort())).thenReturn(true);
        final InetAddressedMessage<AcknowledgementMessage> msg = new InetAddressedMessage<>(acknowledgementMsg, null, inetAddress);

        final InternetDiscoveryChildrenHandler handler = new InternetDiscoveryChildrenHandler(null, myIdentity, currentTime, 0L, 5L, 30L, 60L, superPeers, null, peersManager);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        verify(superPeer).acknowledgementReceived(anyLong());
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
