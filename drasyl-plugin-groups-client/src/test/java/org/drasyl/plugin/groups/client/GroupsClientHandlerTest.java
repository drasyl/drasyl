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
package org.drasyl.plugin.groups.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.Serialization;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.plugin.groups.client.event.GroupJoinFailedEvent;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupLeftEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberLeftEvent;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.plugin.groups.client.message.GroupsClientMessage;
import org.drasyl.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.serialization.JacksonJsonSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Group group;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private GroupUri uri;
    @Mock
    private IdentityPublicKey publicKey;
    private final Duration firstStartDelay = Duration.ofMillis(1);
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Serialization inboundSerialization;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Serialization outboundSerialization;

    @Nested
    class HandlerAdded {
        @Test
        void shouldStartHandler() {
            final GroupsClientHandler handler = new GroupsClientHandler(Set.of(), inboundSerialization, outboundSerialization);

            handler.handlerAdded(ctx);

            verify(inboundSerialization).addSerializer(eq(GroupsServerMessage.class), any(JacksonJsonSerializer.class));
            verify(outboundSerialization).addSerializer(eq(GroupsClientMessage.class), any(JacksonJsonSerializer.class));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldStopRenewTasks(@Mock final Future<?> disposable) {
            final Map<Group, Future<?>> renewTasks = new HashMap<>(Map.of(group, disposable));
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay, inboundSerialization, outboundSerialization);

            handler.handlerRemoved(ctx);

            verify(disposable).cancel(false);
            assertTrue(renewTasks.isEmpty());
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldDeregisterFromGroups() {
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity);
            try {
                pipeline.pipeline().addLast("handler", handler);
                pipeline.pipeline().remove("handler");

                assertEquals(new GroupLeaveMessage(group), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());

                verify(renewTasks).clear();
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassThroughOnNotMatchingEvent(@Mock final NodeOfflineEvent event) {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                pipeline.pipeline().fireUserEventTriggered(event);

                assertEquals(event, pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldSendJoinOnNodeUpEvent(@Mock final NodeUpEvent event) {
            final String credentials = "test";
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                when(uri.getGroup()).thenReturn(group);
                when(uri.getCredentials()).thenReturn(credentials);
                when(identity.getProofOfWork()).thenReturn(proofOfWork);

                pipeline.pipeline().fireUserEventTriggered(event);

                await().untilAsserted(() -> {
                    pipeline.runPendingTasks();
                    assertEquals(event, pipeline.readUserEvent());
                });

                assertEquals(new GroupJoinMessage(uri.getGroup(), uri.getCredentials(), proofOfWork, false), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class Read {
        @Test
        void shouldProcessMemberJoined() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                final MemberJoinedMessage msg = new MemberJoinedMessage(publicKey, group);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));

                assertEquals(GroupMemberJoinedEvent.of(publicKey, group), pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldProcessMemberLeft() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                final MemberLeftMessage msg = new MemberLeftMessage(publicKey, group);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));

                assertEquals(GroupMemberLeftEvent.of(publicKey, group), pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldProcessOwnLeft() {
            when(identity.getIdentityPublicKey()).thenReturn(publicKey);

            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                final MemberLeftMessage msg = new MemberLeftMessage(identity.getIdentityPublicKey(), group);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));

                assertEquals(GroupLeftEvent.of(group, () -> {
                }), pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldProcessWelcome() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                final GroupWelcomeMessage msg = new GroupWelcomeMessage(group, Set.of(publicKey));

                when(groups.get(any())).thenReturn(uri);
                when(uri.getTimeout()).thenReturn(Duration.ofMinutes(10));

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));

                assertEquals(GroupJoinedEvent.of(group, Set.of(publicKey), () -> {
                }), pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldProcessJoinFailed() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay, inboundSerialization, outboundSerialization);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, handler);
            try {
                final GroupJoinFailedMessage.Error error = GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
                final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(group, error);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));

                assertEquals(GroupJoinFailedEvent.of(group, error, () -> {
                }), pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }
    }
}
