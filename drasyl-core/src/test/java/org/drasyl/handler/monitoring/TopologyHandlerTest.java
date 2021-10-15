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
package org.drasyl.handler.monitoring;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemovePathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.handler.monitoring.TopologyHandler.Topology;
import org.drasyl.identity.DrasylAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static test.util.IdentityTestUtil.ID_1;

@ExtendWith(MockitoExtension.class)
class TopologyHandlerTest {
    @Mock
    private Map<DrasylAddress, InetSocketAddress> superPeers;
    @Mock
    protected Map<DrasylAddress, InetSocketAddress> childrenPeers;
    @Mock
    protected Map<DrasylAddress, InetSocketAddress> peers;

    @Nested
    class UserEventTriggered {
        private ChannelHandler handler;

        @BeforeEach
        void setUp() {
            handler = new TopologyHandler(superPeers, childrenPeers, peers) {
            };
        }

        @Test
        void shouldHandleAddPathAndSuperPeerEvent(final @Mock AddPathAndSuperPeerEvent event) {
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            channel.pipeline().fireUserEventTriggered(event);

            verify(superPeers).put(any(), any());
        }

        @Test
        void shouldHandleRemoveSuperPeerAndPathEvent(final @Mock RemoveSuperPeerAndPathEvent event) {
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            channel.pipeline().fireUserEventTriggered(event);

            verify(superPeers).remove(any());
        }

        @Test
        void shouldHandleAddPathAndChildrenEvent(final @Mock AddPathAndChildrenEvent event) {
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            channel.pipeline().fireUserEventTriggered(event);

            verify(childrenPeers).put(any(), any());
        }

        @Test
        void shouldHandleRemoveChildrenAndPathEvent(final @Mock RemoveChildrenAndPathEvent event) {
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            channel.pipeline().fireUserEventTriggered(event);

            verify(childrenPeers).remove(any());
        }

        @Test
        void shouldHandleAddPathEvent(final @Mock AddPathEvent event) {
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            channel.pipeline().fireUserEventTriggered(event);

            verify(peers).put(any(), any());
        }

        @Test
        void shouldHandleRemovePathEvent(final @Mock RemovePathEvent event) {
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            channel.pipeline().fireUserEventTriggered(event);

            verify(peers).remove(any());
        }
    }

    @Nested
    class TopologyTest {
        @Test
        void shouldReturnCurrentTopology(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().localAddress()).thenReturn(ID_1.getAddress());

            final TopologyHandler handler = new TopologyHandler(superPeers, childrenPeers, peers) {
            };

            assertEquals(new Topology(ID_1.getAddress(), superPeers, childrenPeers, peers), handler.topology(ctx));
        }
    }
}
