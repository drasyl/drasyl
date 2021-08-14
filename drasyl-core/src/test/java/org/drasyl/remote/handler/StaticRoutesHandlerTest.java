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

import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.drasyl.channel.AddPathEvent;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.RemovePathEvent;
import org.drasyl.channel.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class StaticRoutesHandlerTest {
    @Test
    void shouldPopulateRoutesOnChannelActive(@Mock final IdentityPublicKey publicKey) {
        final SocketAddress address = new InetSocketAddress(22527);

        final ChannelHandler handler = new StaticRoutesHandler(ImmutableMap.of(publicKey, address));
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
        try {
            channel.pipeline().fireChannelActive();

            assertThat(channel.readUserEvent(), instanceOf(AddPathEvent.class));
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldClearRoutesOnChannelInactive(@Mock final IdentityPublicKey publicKey,
                                            @Mock final SocketAddress address) {
        final ChannelHandler handler = new StaticRoutesHandler(ImmutableMap.of(publicKey, address));
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
        try {
            channel.userEvents().clear();
            channel.pipeline().fireChannelInactive();

            assertThat(channel.readUserEvent(), instanceOf(RemovePathEvent.class));
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final SocketAddress address = new InetSocketAddress(22527);
        final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();

        final ChannelHandler handler = new StaticRoutesHandler(ImmutableMap.of(publicKey, address));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.writeAndFlush(new AddressedMessage<>(message, publicKey));

            final ReferenceCounted actual = channel.readOutbound();
            assertEquals(new AddressedMessage<>(message, address), actual);

            actual.release();
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldPassThroughMessageWhenStaticRouteIsAbsent(@Mock final IdentityPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final ChannelHandler handler = new StaticRoutesHandler(ImmutableMap.of());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.writeAndFlush(new AddressedMessage<>(message, publicKey));

            final ReferenceCounted actual = channel.readOutbound();
            assertEquals(new AddressedMessage<>(message, publicKey), actual);

            actual.release();
        }
        finally {
            channel.close();
        }
    }
}
