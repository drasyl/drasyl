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

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.serialization.Serialization;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientHandlerTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private HashMap<Group, Disposable> renewTasks;
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

    @Nested
    class HandlerAdded {
        @Test
        void shouldStartHandler(@Mock final Serialization inboundSerialization,
                                @Mock final Serialization outboundSerialization) {
            when(ctx.inboundSerialization()).thenReturn(inboundSerialization);
            when(ctx.outboundSerialization()).thenReturn(outboundSerialization);

            final GroupsClientHandler handler = new GroupsClientHandler(Set.of());

            handler.onAdded(ctx);

            verify(inboundSerialization).addSerializer(eq(GroupsServerMessage.class), any(JacksonJsonSerializer.class));
            verify(outboundSerialization).addSerializer(eq(GroupsClientMessage.class), any(JacksonJsonSerializer.class));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldStopRenewTasks(@Mock final Disposable disposable) {
            final Map<Group, Disposable> renewTasks = new HashMap<>(Map.of(group, disposable));
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay);

            handler.onRemoved(ctx);

            verify(disposable).dispose();
            assertTrue(renewTasks.isEmpty());
        }

        @Test
        void shouldDeregisterFromGroups() {
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager);
            try {
                final TestObserver<GroupLeaveMessage> testObserver = pipeline.drasylOutboundMessages(GroupLeaveMessage.class).test();

                pipeline.addLast("handler", handler);
                pipeline.remove("handler");

                testObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupLeaveMessage(group));

                verify(renewTasks).clear();
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassThroughOnNotMatchingEvent(@Mock final NodeOfflineEvent event) {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> testObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                testObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldSendJoinOnNodeUpEvent(@Mock final NodeUpEvent event) {
            final String credentials = "test";
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final TestObserver<GroupJoinMessage> outboundObserver = pipeline.drasylOutboundMessages(GroupJoinMessage.class).test();

                when(uri.getGroup()).thenReturn(group);
                when(uri.getCredentials()).thenReturn(credentials);
                when(identity.getProofOfWork()).thenReturn(proofOfWork);

                pipeline.processInbound(event).join();

                await().untilAsserted(() -> {
                    pipeline.runPendingTasks();
                    eventObserver.awaitCount(1)
                            .assertValueCount(1)
                            .assertValue(event);
                });

                outboundObserver
                        .awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupJoinMessage(uri.getGroup(), uri.getCredentials(), proofOfWork, false));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class Read {
        @Test
        void shouldProcessMemberJoined() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final MemberJoinedMessage msg = new MemberJoinedMessage(publicKey, group);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(GroupMemberJoinedEvent.of(publicKey, group));
                assertTrue(future.isDone());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldProcessMemberLeft() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final MemberLeftMessage msg = new MemberLeftMessage(publicKey, group);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(GroupMemberLeftEvent.of(publicKey, group));
                assertTrue(future.isDone());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldProcessOwnLeft() {
            when(identity.getIdentityPublicKey()).thenReturn(publicKey);

            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final MemberLeftMessage msg = new MemberLeftMessage(identity.getIdentityPublicKey(), group);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(GroupLeftEvent.of(group, () -> {
                        }));
                assertTrue(future.isDone());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        @Test
        void shouldProcessWelcome() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final GroupWelcomeMessage msg = new GroupWelcomeMessage(group, Set.of(publicKey));

                when(groups.get(any())).thenReturn(uri);
                when(uri.getTimeout()).thenReturn(Duration.ofMinutes(10));

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(GroupJoinedEvent.of(group, Set.of(publicKey), () -> {
                        }));
                assertTrue(future.isDone());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldProcessJoinFailed() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final GroupJoinFailedMessage.Error error = GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
                final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(group, error);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(GroupJoinFailedEvent.of(group, error, () -> {
                        }));

                assertTrue(future.isDone());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }
}
