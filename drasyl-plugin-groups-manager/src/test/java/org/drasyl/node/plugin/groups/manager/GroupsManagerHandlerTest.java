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
package org.drasyl.node.plugin.groups.manager;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.node.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.node.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.node.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.node.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.node.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.node.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.node.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.node.plugin.groups.manager.data.Group;
import org.drasyl.node.plugin.groups.manager.data.Member;
import org.drasyl.node.plugin.groups.manager.data.Membership;
import org.drasyl.node.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.node.plugin.groups.manager.database.DatabaseException;
import org.junit.jupiter.api.Assertions;
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
    private Future<?> staleTask;
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
        void shouldAddClassesToValidatorAndAddStaleTask() {
            when(ctx.executor()).thenReturn(scheduler);

            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);

            handler.handlerAdded(ctx);

            verify(scheduler).scheduleWithFixedDelay(any(), eq(1L), eq(1L), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    class StaleTask {
        @Test
        void shouldNotifyAboutStaleMembers() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            when(databaseAdapter.deleteStaleMemberships()).thenReturn(memberships);
            when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);

            handler.staleTask(ctx);

            verify(ctx, times(2)).writeAndFlush(argThat((ArgumentMatcher<AddressedMessage<?, ?>>) m -> m.message() instanceof MemberLeftMessage &&
                    ((MemberLeftMessage) m.message()).getMember().equals(publicKey) &&
                    ((MemberLeftMessage) m.message()).getGroup().equals(org.drasyl.node.plugin.groups.client.Group.of(group.getName()))));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldDisposeStaleTask() {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter, staleTask);

            handler.handlerRemoved(ctx);

            verify(staleTask).cancel(false);
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldSkipOnEvent(@Mock final GroupJoinedEvent event) {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final UserEventAwareEmbeddedChannel channel = new UserEventAwareEmbeddedChannel(handler);
            try {
                channel.pipeline().fireUserEventTriggered(event);

                assertEquals(event, channel.readEvent());
            }
            finally {
                channel.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nested
    class Join {
        @Test
        void shouldHandleJoinRequest() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.addGroupMember(any())).thenReturn(true);
                when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);
                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(true);

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                Assertions.assertEquals(new GroupWelcomeMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), Set.of(publicKey)), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
                Assertions.assertEquals(new MemberJoinedMessage(publicKey, org.drasyl.node.plugin.groups.client.Group.of(group.getName())), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldSendErrorOnNotExistingGroup() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(null);

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                Assertions.assertEquals(new GroupJoinFailedMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldSendErrorOnNotWeakProofOfWork() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(false);

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                assertEquals(new GroupJoinFailedMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), GroupJoinFailedMessage.Error.ERROR_PROOF_TO_WEAK), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldSendErrorOnUnknownException() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.addGroupMember(any())).thenThrow(DatabaseException.class);
                when(databaseAdapter.getGroup(msg.getGroup().getName())).thenReturn(group);
                when(proofOfWork.isValid(any(), anyByte())).thenReturn(true);

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                assertEquals(new GroupJoinFailedMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), GroupJoinFailedMessage.Error.ERROR_UNKNOWN), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
            }
            finally {
                channel.close();
            }
        }

        @Test
        @Timeout(value = 15_000, unit = MILLISECONDS)
        void shouldCompleteFutureExceptionallyOnDatabaseException() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupJoinMessage msg = new GroupJoinMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()), "secret", proofOfWork, false);

                when(databaseAdapter.getGroup(any())).thenThrow(DatabaseException.class);

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                assertNull(channel.readOutbound());
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Leave {
        @Test
        void shouldHandleLeaveRequest() throws DatabaseException {
            when(databaseAdapter.getGroupMembers(group.getName())).thenReturn(memberships);

            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupLeaveMessage msg = new GroupLeaveMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()));

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                assertEquals(new MemberLeftMessage(publicKey, msg.getGroup()), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
                assertEquals(new MemberLeftMessage(publicKey, msg.getGroup()), ((AddressedMessage<Object, SocketAddress>) channel.readOutbound()).message());
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldCompleteExceptionallyOnError() throws DatabaseException {
            final GroupsManagerHandler handler = new GroupsManagerHandler(databaseAdapter);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final GroupLeaveMessage msg = new GroupLeaveMessage(org.drasyl.node.plugin.groups.client.Group.of(group.getName()));

                doThrow(DatabaseException.class).when(databaseAdapter).removeGroupMember(any(), anyString());

                channel.pipeline().fireChannelRead(new AddressedMessage<>(msg, publicKey));
                channel.runPendingTasks();

                assertNull(channel.readOutbound());
            }
            finally {
                channel.close();
            }
        }
    }
}
