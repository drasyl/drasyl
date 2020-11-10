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
package org.drasyl.peer.connection.intravm;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery.InboundMessageHandler;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery.OutboundMessageHandler;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IntraVmDiscoveryTest {
    private final int networkId = 1;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private Pipeline pipeline;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Path path;
    @Mock
    private PeerInformation peerInformation;

    @Nested
    class Constructor {
        @Test
        void shouldAddHandlerToPipeline() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(networkId, publicKey, peersManager, pipeline)) {
                verify(pipeline, times(2)).addBefore(anyString(), anyString(), any());
            }
        }
    }

    @Nested
    class Open {
        @Test
        void shouldAddToDiscoveries() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(networkId, publicKey, peersManager, path, peerInformation, new AtomicBoolean(false))) {
                underTest.open();

                assertThat(IntraVmDiscovery.discoveries, aMapWithSize(1));
            }
        }
    }

    @Nested
    class Close {
        @Test
        void shouldRemovePeerInformation() {
            try (final IntraVmDiscovery underTest = new IntraVmDiscovery(networkId, publicKey, peersManager, path, peerInformation, new AtomicBoolean(true))) {
                underTest.close();

                assertThat(IntraVmDiscovery.discoveries, aMapWithSize(0));
            }
        }
    }

    @Nested
    class InboundMessageHandlerTest {
        @Nested
        class MatchedRead {
            @Test
            void shouldPassMessageIfNotStarted(@Mock final HandlerContext ctx,
                                               @Mock final CompressedPublicKey sender,
                                               @Mock final Message msg,
                                               @Mock final CompletableFuture<Void> future) {
                final InboundMessageHandler underTest = new InboundMessageHandler(new AtomicBoolean(false));
                underTest.matchedRead(ctx, sender, msg, future);

                verify(ctx).fireRead(sender, msg, future);
            }

            @Test
            void shouldPassMessageIfRecipientIsNotKnown(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                        @Mock final CompressedPublicKey sender,
                                                        @Mock final Message msg,
                                                        @Mock final CompletableFuture<Void> future) {
                final InboundMessageHandler underTest = new InboundMessageHandler(new AtomicBoolean(true));
                underTest.matchedRead(ctx, sender, msg, future);

                verify(ctx).fireRead(sender, msg, future);
            }
        }
    }

    @Nested
    class OutboundMessageHandlerTest {
        @Nested
        class MatchedWrite {
            @Test
            void shouldPassMessageIfNotStarted(@Mock final HandlerContext ctx,
                                               @Mock final CompressedPublicKey recipient,
                                               @Mock final Message msg,
                                               @Mock final CompletableFuture<Void> future) {
                final OutboundMessageHandler underTest = new OutboundMessageHandler(new AtomicBoolean(false));
                underTest.matchedWrite(ctx, recipient, msg, future);

                verify(ctx).write(recipient, msg, future);
            }

            @Test
            void shouldPassMessageIfRecipientIsNotKnown(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                        @Mock final CompressedPublicKey recipient,
                                                        @Mock final Message msg,
                                                        @Mock final CompletableFuture<Void> future) {
                final OutboundMessageHandler underTest = new OutboundMessageHandler(new AtomicBoolean(true));
                underTest.matchedWrite(ctx, recipient, msg, future);

                verify(ctx).write(recipient, msg, future);
            }
        }
    }
}