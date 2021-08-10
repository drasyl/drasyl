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
import io.netty.util.ReferenceCounted;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.remote.protocol.ApplicationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaticRoutesHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldPopulateRoutesOnChannelActive(@Mock final IdentityPublicKey publicKey) {
        final SocketAddress address = new InetSocketAddress(22527);
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE);
        try {
            pipeline.pipeline().fireChannelActive();

            verify(peersManager).addPath(any(), eq(publicKey), any());
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldClearRoutesOnChannelInactive(@Mock final IdentityPublicKey publicKey,
                                            @Mock final SocketAddress address) {
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE);
        try {
            pipeline.pipeline().fireChannelInactive();

            verify(peersManager).removePath(any(), eq(publicKey), any());
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        final SocketAddress address = new InetSocketAddress(22527);
        final IdentityPublicKey publicKey = IdentityTestUtil.ID_2.getIdentityPublicKey();
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of(publicKey, address));

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(message, publicKey));

            final ReferenceCounted actual = pipeline.readOutbound();
            assertEquals(new AddressedMessage<>(message, address), actual);

            actual.release();
        }
        finally {
            pipeline.close();
        }
    }

    @Test
    void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final IdentityPublicKey publicKey,
                                                         @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
        when(config.getRemoteStaticRoutes()).thenReturn(ImmutableMap.of());

        final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, IdentityTestUtil.ID_1, peersManager, StaticRoutesHandler.INSTANCE);
        try {
            pipeline.writeAndFlush(new AddressedMessage<>(message, publicKey));

            final ReferenceCounted actual = pipeline.readOutbound();
            assertEquals(new AddressedMessage<>(message, publicKey), actual);

            actual.release();
        }
        finally {
            pipeline.close();
        }
    }
}
