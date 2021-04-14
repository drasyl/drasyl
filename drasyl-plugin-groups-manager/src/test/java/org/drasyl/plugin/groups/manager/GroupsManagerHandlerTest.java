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
package org.drasyl.plugin.groups.manager;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.plugin.groups.client.message.GroupsClientMessage;
import org.drasyl.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.data.Member;
import org.drasyl.plugin.groups.manager.data.Membership;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.drasyl.serialization.JacksonJsonSerializer;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.RxJavaTestUtil;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_PROOF_TO_WEAK;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsManagerHandlerTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private DatabaseAdapter databaseAdapter;
    @Mock
    private DrasylScheduler scheduler;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private Pipeline pipeline;
    @Mock
    private Disposable staleTask;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private ProofOfWork proofOfWork;
    private Set<Membership> memberships;
    private Group group;

    @BeforeEach
    void setUp() {
        final Member member = Member.of(publicKey);
        group = Group.of("name", "secret", (byte) 0, Duration.ofSeconds(60));
        final Membership membership = Membership.of(member, group, 60L);
        memberships = Set.of(membership);
    }

    @Nested
    class HandlerAdded {
        @Test
        void shouldAddClassesToValidatorAndAddStaleTask(@Mock final Serialization inboundSerialization,
                                                        @Mock final Serialization outboundSerialization) {
            when(ctx.inboundSerialization()).thenReturn(inboundSerialization);
            when(ctx.outboundSerialization()).thenReturn(outboundSerialization);
            when(ctx.independentScheduler()).thenReturn(scheduler);

            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);

            handler.onAdded(ctx);

            verify(inboundSerialization).addSerializer(eq(GroupsClientMessage.class), any(JacksonJsonSerializer.class));
            verify(outboundSerialization).addSerializer(eq(GroupsServerMessage.class), any(JacksonJsonSerializer.class));
            verify(scheduler).schedulePeriodicallyDirect(any(), eq(1L), eq(1L), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    class StaleTask {
        @Test
        void shouldNotifyAboutStaleMembers() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            when(databaseAdapter.deleteStaleMemberships()).thenReturn(memberships);
            when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);
            when(ctx.pipeline()).thenReturn(pipeline);
            when(pipeline.processOutbound(any(), any())).thenReturn(new CompletableFuture<>());

            handler.staleTask(ctx);

            verify(pipeline, times(2)).processOutbound(publicKey, new MemberLeftMessage(publicKey, org.drasyl.plugin.groups.client.Group.of(group.getName())));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldDisposeStaleTask() {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, staleTask);

            handler.onRemoved(ctx);

            verify(staleTask).dispose();
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldSkipOnEvent(@Mock final GroupJoinedEvent event) {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> testObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                testObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
        }
    }

    @Nested
    class Join {
        @Test
        void shouldHandleJoinRequest() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<GroupsServerMessage> testObserver = pipeline.outboundMessages(GroupsServerMessage.class).test();
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.addGroupMember(any())).thenReturn(true);
                when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);
                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(true);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                RxJavaTestUtil.assertValues(testObserver.awaitCount(2),
                        new GroupWelcomeMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), Set.of(publicKey)),
                        new MemberJoinedMessage(publicKey, org.drasyl.plugin.groups.client.Group.of(group.getName())));
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldSendErrorOnNotExistingGroup() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<GroupsServerMessage> testObserver = pipeline.outboundMessages(GroupsServerMessage.class).test();
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(null);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                assertThrows(CompletionException.class, future::join);
                testObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValues(
                                new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_GROUP_NOT_FOUND));
                assertTrue(future.isCompletedExceptionally());
            }
        }

        @Test
        void shouldSendErrorOnNotWeakProofOfWork() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<GroupsServerMessage> testObserver = pipeline.outboundMessages(GroupsServerMessage.class).test();
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(false);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                assertThrows(CompletionException.class, future::join);
                testObserver.awaitCount(1).assertValueCount(1);
                testObserver.assertValues(
                        new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_PROOF_TO_WEAK));
                assertTrue(future.isCompletedExceptionally());
            }
        }

        @Test
        void shouldSendErrorOnUnknownException() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<GroupsServerMessage> testObserver = pipeline.outboundMessages(GroupsServerMessage.class).test();
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.addGroupMember(any())).thenThrow(DatabaseException.class);
                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(true);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                assertThrows(CompletionException.class, future::join);
                testObserver.awaitCount(1).assertValueCount(1);
                testObserver.assertValues(
                        new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_UNKNOWN));
                assertTrue(future.isCompletedExceptionally());
            }
        }

        @Test
        @Timeout(value = 15_000, unit = MILLISECONDS)
        void shouldCompleteFutureExceptionallyOnDatabaseException() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(any())).thenThrow(DatabaseException.class);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                assertThrows(Exception.class, future::get);
            }
        }
    }

    @Nested
    class Leave {
        @Test
        void shouldHandleLeaveRequest() throws DatabaseException {
            when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);

            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<GroupsServerMessage> outboundMessages = pipeline.outboundMessages(GroupsServerMessage.class).test();
                final GroupLeaveMessage msg = new GroupLeaveMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()));

                pipeline.processInbound(publicKey, msg).join();

                outboundMessages
                        .awaitCount(2)
                        .assertValueCount(2)
                        .assertValues(
                                new MemberLeftMessage(publicKey, msg.getGroup()),
                                new MemberLeftMessage(publicKey, msg.getGroup())
                        );
            }
        }

        @Test
        void shouldCompleteExceptionallyOnError() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<GroupsServerMessage> testObserver = pipeline.outboundMessages(GroupsServerMessage.class).test();
                final GroupLeaveMessage msg = new GroupLeaveMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()));

                doThrow(DatabaseException.class).when(databaseAdapter).removeGroupMember(any(), anyString());

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                testObserver.assertNoValues();
                assertThrows(CompletionException.class, future::join);
            }
        }
    }
}
