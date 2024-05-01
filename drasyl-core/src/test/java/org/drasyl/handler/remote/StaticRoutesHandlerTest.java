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
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandler;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

import static org.drasyl.handler.remote.StaticRoutesHandler.PATH_ID;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticRoutesHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;

    @Test
    void shouldPopulateRoutesOnChannelActive(@Mock final IdentityPublicKey publicKey) {
        when(config.getPeersManager().addChildrenPath(any(), any(), any(), any(), anyInt())).thenReturn(true);

        final InetSocketAddress address = new InetSocketAddress(22527);

        final ChannelHandler handler = new StaticRoutesHandler(Map.of(publicKey, address));
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, handler);
        try {
            channel.pipeline().fireChannelActive();

            verify(config.getPeersManager()).addChildrenPath(any(), any(), any(), any(), anyInt());
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldClearRoutesOnChannelInactive(@Mock final IdentityPublicKey publicKey,
                                            @Mock final InetSocketAddress address) {
        when(config.getPeersManager().getPeers(PATH_ID)).thenReturn(Set.of(publicKey));
        final ChannelHandler handler = new StaticRoutesHandler(Map.of(publicKey, address));
        final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, handler);
        try {
            channel.userEvents().clear();
            channel.pipeline().fireChannelInactive();

            assertThat(channel.readEvent(), instanceOf(RemovePathEvent.class));
        }
        finally {
            channel.close();
        }
    }

    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final InetSocketAddress address = new InetSocketAddress(22527);
        final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();

        final ChannelHandler handler = new StaticRoutesHandler(Map.of(publicKey, address));
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.writeAndFlush(new OverlayAddressedMessage<>(message, publicKey));

            final ReferenceCounted actual = channel.readOutbound();
            assertEquals(new InetAddressedMessage<>(message, address), actual);

            actual.release();
        }
        finally {
            channel.checkException();
            channel.close();
        }
    }

    @Test
    void shouldPassThroughMessageWhenStaticRouteIsAbsent(@Mock final IdentityPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final ChannelHandler handler = new StaticRoutesHandler(Map.of());
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.writeAndFlush(new OverlayAddressedMessage<>(message, publicKey));

            verify(config.getPeersManager()).removeChildrenPaths(any(), any());
        }
        finally {
            channel.checkException();
            channel.close();
        }
    }
}
