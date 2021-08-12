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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.Serialization;
import org.drasyl.channel.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_PROOF_TO_WEAK;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsManagerHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ChannelHandlerContext ctx;
    @Mock
    private DatabaseAdapter databaseAdapter;
    @Mock
    private EventExecutor scheduler;
    @Mock
    private IdentityPublicKey publicKey;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private Future<?> staleTask;
    @Mock
    private ProofOfWork proofOfWork;
    private Set<Membership> memberships;
    private Group group;
    @Mock
    private Serialization inboundSerialization;
    @Mock
    private Serialization outboundSerialization;

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
            when(ctx.executor()).thenReturn(scheduler);

            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);

            handler.handlerAdded(ctx);

            verify(inboundSerialization).addSerializer(eq(GroupsClientMessage.class), any(JacksonJsonSerializer.class));
            verify(outboundSerialization).addSerializer(eq(GroupsServerMessage.class), any(JacksonJsonSerializer.class));
            verify(scheduler).scheduleAtFixedRate(any(), eq(1L), eq(1L), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    class StaleTask {
        @Test
        void shouldNotifyAboutStaleMembers() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            when(databaseAdapter.deleteStaleMemberships()).thenReturn(memberships);
            when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);

            handler.staleTask(ctx);

            verify(ctx, times(2)).writeAndFlush(argThat((ArgumentMatcher<AddressedMessage<?, ?>>) m -> m.message() instanceof MemberLeftMessage &&
                    ((MemberLeftMessage) m.message()).getMember().equals(publicKey) &&
                    ((MemberLeftMessage) m.message()).getGroup().equals(org.drasyl.plugin.groups.client.Group.of(group.getName()))));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldDisposeStaleTask() {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, staleTask, inboundSerialization, outboundSerialization);

            handler.handlerRemoved(ctx);

            verify(staleTask).cancel(false);
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldSkipOnEvent(@Mock final GroupJoinedEvent event) {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final UserEventAwareEmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                pipeline.pipeline().fireUserEventTriggered(event);

                assertEquals(event, pipeline.readUserEvent());
            }
            finally {
                pipeline.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nested
    class Join {
        @Test
        void shouldHandleJoinRequest() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.addGroupMember(any())).thenReturn(true);
                when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);
                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(true);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertEquals(new GroupWelcomeMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), Set.of(publicKey)), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
                assertEquals(new MemberJoinedMessage(publicKey, org.drasyl.plugin.groups.client.Group.of(group.getName())), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldSendErrorOnNotExistingGroup() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(null);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertEquals(new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_GROUP_NOT_FOUND), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldSendErrorOnNotWeakProofOfWork() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(false);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertEquals(new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_PROOF_TO_WEAK), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldSendErrorOnUnknownException() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.addGroupMember(any())).thenThrow(DatabaseException.class);
                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(true);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertEquals(new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_UNKNOWN), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        @Timeout(value = 15_000, unit = MILLISECONDS)
        void shouldCompleteFutureExceptionallyOnDatabaseException() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(any())).thenThrow(DatabaseException.class);

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertNull(pipeline.readOutbound());
            }
            finally {
                pipeline.close();
            }
        }
    }

    @Nested
    class Leave {
        @Test
        void shouldHandleLeaveRequest() throws DatabaseException {
            when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);

            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupLeaveMessage msg = new GroupLeaveMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()));

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertEquals(new MemberLeftMessage(publicKey, msg.getGroup()), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
                assertEquals(new MemberLeftMessage(publicKey, msg.getGroup()), ((AddressedMessage<Object, SocketAddress>) pipeline.readOutbound()).message());
            }
            finally {
                pipeline.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyOnError() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, inboundSerialization, outboundSerialization);
            final EmbeddedChannel pipeline = new UserEventAwareEmbeddedChannel(handler);
            try {
                final GroupLeaveMessage msg = new GroupLeaveMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()));

                doThrow(DatabaseException.class).when(databaseAdapter).removeGroupMember(any(), anyString());

                pipeline.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                pipeline.runPendingTasks();

                assertNull(pipeline.readOutbound());
            }
            finally {
                pipeline.close();
            }
        }
    }
}
