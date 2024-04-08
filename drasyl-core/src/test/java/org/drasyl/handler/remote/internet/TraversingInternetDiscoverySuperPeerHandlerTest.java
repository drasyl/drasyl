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
import org.drasyl.handler.remote.PeersManager;
import org.drasyl.handler.remote.internet.InternetDiscoverySuperPeerHandler.ChildrenPeer;
import org.drasyl.handler.remote.protocol.ApplicationMessage;
import org.drasyl.handler.remote.protocol.HopCount;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.util.Pair;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraversingInternetDiscoverySuperPeerHandlerTest {
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
    void shouldInitiateRendezvousWhenRelayingMessageBetweenTwoChildrenPeers(@Mock final IdentityPublicKey publicKeyA,
                                                                            @Mock final ChildrenPeer childrenPeerA,
                                                                            @Mock final IdentityPublicKey publicKeyB,
                                                                            @Mock final ChildrenPeer childrenPeerB,
                                                                            @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage applicationMsg,
                                                                            @Mock final InetSocketAddress inetAddress) {
        final InetSocketAddress childrenAInetAddress = new InetSocketAddress(22527);
        final InetSocketAddress childrenBInetAddress = new InetSocketAddress(22528);
        when(childrenPeerA.publicInetAddress()).thenReturn(childrenAInetAddress);
        when(childrenPeerB.publicInetAddress()).thenReturn(childrenBInetAddress);
        final Map<DrasylAddress, ChildrenPeer> childrenPeers = new HashMap<>(Map.of(publicKeyA, childrenPeerA, publicKeyB, childrenPeerB));
        when(applicationMsg.getRecipient()).thenReturn(publicKeyA);
        when(applicationMsg.getSender()).thenReturn(publicKeyB);
        when(applicationMsg.incrementHopCount()).thenReturn(applicationMsg);
        final InetAddressedMessage<ApplicationMessage> msg = new InetAddressedMessage<>(applicationMsg, null, inetAddress);
        final Set<Pair<DrasylAddress, DrasylAddress>> uniteAttemptsCache = new HashSet<>();

        final TraversingInternetDiscoverySuperPeerHandler handler = new TraversingInternetDiscoverySuperPeerHandler(0, myPublicKey, myProofOfWork, currentTime, 5L, 30L, 60L, peersManager, hopLimit, childrenPeers, null, uniteAttemptsCache);
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);

        channel.writeInbound(msg);

        // relayed message
        final InetAddressedMessage<RemoteMessage> relayedMsg = channel.readOutbound();
        assertSame(applicationMsg, relayedMsg.content());
        assertSame(childrenAInetAddress, relayedMsg.recipient());

        // rendezvous message to sender
        final InetAddressedMessage<UniteMessage> rendezvousMsgB = channel.readOutbound();
        assertThat(rendezvousMsgB.content(), instanceOf(UniteMessage.class));
        assertSame(childrenBInetAddress, rendezvousMsgB.recipient());

        // rendezvous message to recipient
        final InetAddressedMessage<UniteMessage> rendezvousMsgA = channel.readOutbound();
        assertThat(rendezvousMsgA.content(), instanceOf(UniteMessage.class));
        assertSame(childrenAInetAddress, rendezvousMsgA.recipient());

        assertFalse(uniteAttemptsCache.isEmpty());
    }
}
