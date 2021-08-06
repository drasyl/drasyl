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
package org.drasyl.intravm;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.channel.MigrationInboundMessage;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntraVmDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final Map<Pair<Integer, IdentityPublicKey>, MigrationHandlerContext> discoveries = new HashMap<>();
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;

    @Nested
    class StartDiscovery {
        @Test
        void shouldStartDiscoveryOnNodeUpEvent(@Mock final NodeUpEvent event) {
            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                assertThat(discoveries, aMapWithSize(1));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                              @Mock final MigrationHandlerContext ctx) {
            discoveries.put(Pair.of(0, identity.getIdentityPublicKey()), ctx);
            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                assertThat(discoveries, aMapWithSize(0));
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event,
                                                @Mock final MigrationHandlerContext ctx) {
            discoveries.put(Pair.of(0, identity.getIdentityPublicKey()), ctx);

            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                assertThat(discoveries, aMapWithSize(0));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldSendOutgoingMessageToKnownRecipient(@Mock final IdentityPublicKey recipient,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                       @Mock final MigrationHandlerContext ctx) {
            discoveries.put(Pair.of(0, recipient), ctx);
            when(ctx.fireChannelRead(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked") final MigrationInboundMessage msg = invocation.getArgument(0, MigrationInboundMessage.class);
                msg.future().complete(null);
                return null;
            });

            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processOutbound(recipient, message).join();

                verify(ctx).fireChannelRead(any());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPasstroughOutgoingMessageForUnknownRecipients(@Mock final IdentityPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Object message) {
            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Object> outboundMessages = pipeline.drasylOutboundMessages().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.assertValueCount(1);
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
