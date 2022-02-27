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
package org.drasyl.node.plugin.groups.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.plugin.groups.client.event.GroupJoinFailedEvent;
import org.drasyl.node.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.node.plugin.groups.client.event.GroupLeftEvent;
import org.drasyl.node.plugin.groups.client.event.GroupMemberJoinedEvent;
import org.drasyl.node.plugin.groups.client.event.GroupMemberLeftEvent;
import org.drasyl.node.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.node.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.node.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.node.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.node.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.node.plugin.groups.client.message.MemberLeftMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelHandlerContext ctx;
    @Mock
    private HashMap<Group, Future<?>> renewTasks;
    @Mock
    private Map<Group, GroupUri> groups;
    @Mock
    private Identity identity;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Group group;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private GroupUri uri;
    @Mock
    private IdentityPublicKey publicKey;
    private final Duration firstStartDelay = Duration.ofMillis(1);

    @Nested
    class HandlerRemoved {
        @Test
        void shouldStopRenewTasks(@Mock final Future<?> disposable) {
            final Map<Group, Future<?>> renewTasks = new HashMap<>(Map.of(group, disposable));
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay, identity);

            handler.handlerRemoved(ctx);

            verify(disposable).cancel(false);
            assertTrue(renewTasks.isEmpty());
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldDeregisterFromGroups() {
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay, identity);
            final EmbeddedChannel channel = new EmbeddedChannel();
            try {
                channel.pipeline().addLast("handler", handler);
                channel.pipeline().remove("handler");

                Assertions.assertEquals(GroupLeaveMessage.of(group), ((OverlayAddressedMessage<Object>) channel.readOutbound()).content());

                verify(renewTasks).clear();
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassThroughOnNotMatchingEvent(@Mock final NodeOfflineEvent event) {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                channel.pipeline().fireUserEventTriggered(event);

                assertEquals(event, channel.readEvent());
            }
            finally {
                channel.close();
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldSendJoinOnChannelactive(@Mock final NodeUpEvent event) {
            final String credentials = "test";
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            when(uri.getGroup()).thenReturn(group);
            when(uri.getCredentials()).thenReturn(credentials);
            when(identity.getProofOfWork()).thenReturn(proofOfWork);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                await().untilAsserted(() -> {
                    channel.runPendingTasks();
                    assertEquals(GroupJoinMessage.of(uri.getGroup(), uri.getCredentials(), proofOfWork, false), ((OverlayAddressedMessage<Object>) channel.readOutbound()).content());
                });
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Read {
        @Test
        void shouldProcessMemberJoined() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                final MemberJoinedMessage msg = MemberJoinedMessage.of(publicKey, group);

                channel.pipeline().fireChannelRead(new OverlayAddressedMessage<>(msg, null, publicKey));

                Assertions.assertEquals(GroupMemberJoinedEvent.of(publicKey, group), channel.readEvent());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldProcessMemberLeft() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                final MemberLeftMessage msg = MemberLeftMessage.of(publicKey, group);

                channel.pipeline().fireChannelRead(new OverlayAddressedMessage<>(msg, null, publicKey));

                Assertions.assertEquals(GroupMemberLeftEvent.of(publicKey, group), channel.readEvent());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldProcessOwnLeft() {
            when(identity.getIdentityPublicKey()).thenReturn(publicKey);

            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                final MemberLeftMessage msg = MemberLeftMessage.of(identity.getIdentityPublicKey(), group);

                channel.pipeline().fireChannelRead(new OverlayAddressedMessage<>(msg, null, publicKey));

                Assertions.assertEquals(GroupLeftEvent.of(group, () -> {
                }), channel.readEvent());
            }
            finally {
                channel.close();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldProcessWelcome() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupWelcomeMessage msg = GroupWelcomeMessage.of(group, Set.of(publicKey));

                when(groups.get(any())).thenReturn(uri);
                when(uri.getTimeout()).thenReturn(Duration.ofMinutes(10));

                channel.pipeline().fireChannelRead(new OverlayAddressedMessage<>(msg, null, publicKey));

                Assertions.assertEquals(GroupJoinedEvent.of(group, Set.of(publicKey), () -> {
                }), channel.readEvent());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldProcessJoinFailed() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, identity);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupJoinFailedMessage.Error error = GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
                final GroupJoinFailedMessage msg = GroupJoinFailedMessage.of(group, error);

                channel.pipeline().fireChannelRead(new OverlayAddressedMessage<>(msg, null, publicKey));

                Assertions.assertEquals(GroupJoinFailedEvent.of(group, error, () -> {
                }), channel.readEvent());
            }
            finally {
                channel.close();
            }
        }
    }
}
