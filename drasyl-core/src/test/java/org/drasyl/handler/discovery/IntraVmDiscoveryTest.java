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
package org.drasyl.handler.discovery;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IntraVmDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    private final Map<Pair<Integer, DrasylAddress>, ChannelHandlerContext> discoveries = new HashMap<>();
    private final int myNetworkId = 0;

    @Nested
    class StartDiscovery {
        @Test
        void shouldStartDiscoveryOnChannelActive() {
            IntraVmDiscovery.discoveries = discoveries;
            final IntraVmDiscovery handler = new IntraVmDiscovery(myNetworkId);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.pipeline().fireChannelActive();

                assertThat(discoveries, aMapWithSize(1));
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnChannelInactive(@Mock final ChannelHandlerContext ctx) {
            IntraVmDiscovery.discoveries = discoveries;
            discoveries.put(Pair.of(0, identity.getAddress()), ctx);
            final IntraVmDiscovery handler = new IntraVmDiscovery(myNetworkId);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.pipeline().fireChannelInactive();

                assertThat(discoveries, aMapWithSize(0));
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldSendOutgoingMessageToKnownRecipient(@Mock final IdentityPublicKey recipient,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final Object message,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            IntraVmDiscovery.discoveries = discoveries;
            discoveries.put(Pair.of(0, recipient), ctx);

            final IntraVmDiscovery handler = new IntraVmDiscovery(myNetworkId);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                channel.writeAndFlush(new OverlayAddressedMessage<>(message, recipient));

                verify(ctx).fireChannelRead(any());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldPasstroughOutgoingMessageForUnknownRecipients(@Mock final IdentityPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final Object message) {
            IntraVmDiscovery.discoveries = discoveries;
            final IntraVmDiscovery handler = new IntraVmDiscovery(myNetworkId);
            final EmbeddedChannel pichanneleline = new UserEventAwareEmbeddedChannel(identity.getAddress(), handler);
            try {
                pichanneleline.writeAndFlush(new OverlayAddressedMessage<>(message, recipient));

                final ReferenceCounted actual = pichanneleline.readOutbound();
                assertEquals(new OverlayAddressedMessage<>(message, recipient), actual);

                actual.release();
            }
            finally {
                pichanneleline.close();
            }
        }
    }
}
