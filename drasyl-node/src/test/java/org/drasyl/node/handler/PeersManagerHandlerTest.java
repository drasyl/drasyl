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
package org.drasyl.node.handler;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.handler.discovery.AddPathAndChildrenEvent;
import org.drasyl.handler.discovery.AddPathAndSuperPeerEvent;
import org.drasyl.handler.discovery.RemoveChildrenAndPathEvent;
import org.drasyl.handler.discovery.RemoveSuperPeerAndPathEvent;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.Peer;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.SetMultimap;
import org.drasyl.util.SetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Disabled
class PeersManagerHandlerTest {
    private SetMultimap<DrasylAddress, Object> paths;
    private Set<DrasylAddress> children;
    private Set<DrasylAddress> superPeers;
    private PeersManagerHandler underTest;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannel channel;

    @BeforeEach
    void setUp() {
        paths = new HashSetMultimap<>();
        children = new HashSet<>();
        superPeers = new HashSet<>();
        underTest = new PeersManagerHandler(paths, children, superPeers);
    }

    @Nested
    class AddPath {
        @Test
        void shouldEmitEventIfThisIsTheFirstPath(@Mock final ChannelHandlerContext ctx,
                                                 @Mock final IdentityPublicKey publicKey,
                                                 @Mock final InetSocketAddress inetAddress,
                                                 @Mock final Object path) {
            underTest.userEventTriggered(ctx, AddPathAndChildrenEvent.of(publicKey, inetAddress, path));

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<Object>) e -> PeerDirectEvent.of(Peer.of(publicKey)).equals(e)));
        }

        @Test
        void shouldEmitNotEventIfPeerHasAlreadyPaths(@Mock final ChannelHandlerContext ctx,
                                                     @Mock final IdentityPublicKey publicKey,
                                                     @Mock final InetSocketAddress inetAddress,
                                                     @Mock final Object path1,
                                                     @Mock final Object path2) {
            paths.put(publicKey, path1);

            underTest.userEventTriggered(ctx, AddPathAndChildrenEvent.of(publicKey, inetAddress, path2));

            verify(ctx, never()).fireUserEventTriggered(any(Event.class));
        }
    }

    @Nested
    class RemoveSuperPeerAndPath {
        @Test
        void shouldRemoveSuperPeerAndPath(@Mock final ChannelHandlerContext ctx,
                                          @Mock final IdentityPublicKey publicKey,
                                          @Mock final Object path) {
            underTest.userEventTriggered(ctx, RemoveSuperPeerAndPathEvent.of(publicKey, path));

            assertEquals(Set.of(), superPeers);
        }

        @Test
        void shouldEmitNodeOfflineEventWhenRemovingLastSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                 @Mock final IdentityPublicKey publicKey,
                                                                 @Mock final Object path) {
            when(ctx.channel()).thenReturn(channel);

            superPeers.add(publicKey);

            underTest.userEventTriggered(ctx, RemoveSuperPeerAndPathEvent.of(publicKey, path));

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<Object>) e -> e instanceof NodeOfflineEvent));
        }

        @Test
        void shouldNotEmitNodeOfflineEventWhenRemovingNonLastSuperPeer(@Mock final ChannelHandlerContext ctx,
                                                                       @Mock final IdentityPublicKey publicKey,
                                                                       @Mock final IdentityPublicKey publicKey2,
                                                                       @Mock final Object path) {
            superPeers.add(publicKey2);
            superPeers.add(publicKey);

            underTest.userEventTriggered(ctx, RemoveSuperPeerAndPathEvent.of(publicKey, path));

            verify(ctx, never()).fireUserEventTriggered(argThat((ArgumentMatcher<Object>) e -> e instanceof NodeOfflineEvent));
        }
    }

    @Nested
    class AddPathAndSuperPeer {
        @Test
        void shouldAddPathAndAddSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                          @Mock final IdentityPublicKey publicKey,
                                          @Mock final InetSocketAddress inetAddress,
                                          @Mock final Object path) {
            when(ctx.channel()).thenReturn(channel);

            underTest.userEventTriggered(ctx, AddPathAndSuperPeerEvent.of(publicKey, inetAddress, path, 123L));

            assertEquals(Set.of(publicKey), superPeers);
            assertEquals(Set.of(path), paths.get(publicKey));
        }

        @Test
        void shouldEmitPeerDirectEventForSuperPeerAndNodeOnlineEvent(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                     @Mock final IdentityPublicKey publicKey,
                                                                     @Mock final InetSocketAddress inetAddress,
                                                                     @Mock final Object path) {
            when(ctx.channel()).thenReturn(channel);

            underTest.userEventTriggered(ctx, AddPathAndSuperPeerEvent.of(publicKey, inetAddress, path, 123L));

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<Object>) e -> PeerDirectEvent.of(Peer.of(publicKey)).equals(e)));
            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<Object>) e -> e instanceof NodeOnlineEvent));
        }
    }

    @Nested
    class RemoveChildrenAndPath {
        @Test
        void shouldRemoveChildrenAndPath(@Mock final ChannelHandlerContext ctx,
                                         @Mock final IdentityPublicKey publicKey,
                                         @Mock final Object path) {
            paths.put(publicKey, path);

            underTest.userEventTriggered(ctx, RemoveChildrenAndPathEvent.of(publicKey, path));

            assertThat(SetUtil.merge(paths.keySet(), SetUtil.merge(superPeers, children)), not(hasItem(publicKey)));
            assertEquals(Set.of(), children);
        }

        @Test
        void shouldNotEmitEventWhenRemovingUnknownPeer(@Mock final ChannelHandlerContext ctx,
                                                       @Mock final IdentityPublicKey publicKey,
                                                       @Mock final Object path) {
            underTest.userEventTriggered(ctx, RemoveChildrenAndPathEvent.of(publicKey, path));

            verify(ctx, never()).fireUserEventTriggered(any(Event.class));
        }
    }

    @Nested
    class AddPathAndChildren {
        @Test
        void shouldAddPathAndChildren(@Mock final ChannelHandlerContext ctx,
                                      @Mock final IdentityPublicKey publicKey,
                                      @Mock final InetSocketAddress inetAddress,
                                      @Mock final Object path) {
            underTest.userEventTriggered(ctx, AddPathAndChildrenEvent.of(publicKey, inetAddress, path));

            assertThat(SetUtil.merge(paths.keySet(), SetUtil.merge(superPeers, children)), hasItem(publicKey));
            assertEquals(Set.of(publicKey), children);
        }

        @Test
        void shouldEmitPeerDirectEventIfGivenPathIsTheFirstOneForThePeer(@Mock final ChannelHandlerContext ctx,
                                                                         @Mock final IdentityPublicKey publicKey,
                                                                         @Mock final InetSocketAddress inetAddress,
                                                                         @Mock final Object path) {
            underTest.userEventTriggered(ctx, AddPathAndChildrenEvent.of(publicKey, inetAddress, path));

            verify(ctx).fireUserEventTriggered(argThat((ArgumentMatcher<Object>) e -> PeerDirectEvent.of(Peer.of(publicKey)).equals(e)));
        }

        @Test
        void shouldEmitNoEventIfGivenPathIsNotTheFirstOneForThePeer(@Mock final ChannelHandlerContext ctx,
                                                                    @Mock final IdentityPublicKey publicKey,
                                                                    @Mock final InetSocketAddress inetAddress,
                                                                    @Mock final Object path,
                                                                    @Mock final Object o) {
            paths.put(publicKey, o);

            underTest.userEventTriggered(ctx, AddPathAndChildrenEvent.of(publicKey, inetAddress, path));

            verify(ctx, never()).fireUserEventTriggered(any(Event.class));
        }
    }
}
