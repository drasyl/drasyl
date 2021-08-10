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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.plugin.groups.client.message.GroupsClientMessage;
import org.drasyl.plugin.groups.client.message.GroupsPluginMessage;
import org.drasyl.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.data.Member;
import org.drasyl.plugin.groups.manager.data.Membership;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.drasyl.serialization.JacksonJsonSerializer;
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.drasyl.channel.DefaultDrasylServerChannel.INBOUND_SERIALIZATION_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.OUTBOUND_SERIALIZATION_ATTR_KEY;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_PROOF_TO_WEAK;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_UNKNOWN;

public class GroupsManagerHandler extends SimpleChannelInboundHandler<AddressedMessage<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsManagerHandler.class);
    private final DatabaseAdapter database;
    private Future<?> staleTask;

    GroupsManagerHandler(final DatabaseAdapter database,
                         final Future<?> staleTask) {
        this.database = database;
        this.staleTask = staleTask;
    }

    public GroupsManagerHandler(final DatabaseAdapter database) {
        this(database, null);
    }

    /**
     * Deletes the stale memberships.
     *
     * @param ctx the handler context
     */
    void staleTask(final ChannelHandlerContext ctx) {
        try {
            for (final Membership member : database.deleteStaleMemberships()) {
                final MemberLeftMessage leftMessage = new MemberLeftMessage(
                        member.getMember().getPublicKey(),
                        org.drasyl.plugin.groups.client.Group.of(member.getGroup().getName()));

                ctx.writeAndFlush(new AddressedMessage<>(leftMessage, member.getMember().getPublicKey()));
                notifyMembers(ctx, member.getGroup().getName(), leftMessage, new CompletableFuture<>());
                LOG.debug("Remove stale member `{}` from group `{}`", member.getMember()::getPublicKey, member.getGroup()::getName);
            }
        }
        catch (final DatabaseException e) {
            LOG.warn("Error occurred during deletion of stale memberships: ", e);
        }
    }

    /**
     * Notifies all members of the given {@code group} with the {@code msg}.
     *
     * @param ctx   the handling context
     * @param group the group that should be notified
     * @param msg   the message that should send to all members of the given {@code group}
     */
    private void notifyMembers(final ChannelHandlerContext ctx,
                               final String group,
                               final GroupsPluginMessage msg,
                               final CompletableFuture<Void> future) throws DatabaseException {
        try {
            final Set<Membership> recipients = database.getGroupMembers(group);

            final FutureCombiner combiner = FutureCombiner.getInstance();

            recipients.forEach(member -> {
                final CompletableFuture<Void> future1 = new CompletableFuture<>();
                FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(msg, member.getMember().getPublicKey())))).combine(future1);
                combiner.add(future1);
            });

            combiner.combine(future);
        }
        catch (final DatabaseException e) {
            LOG.debug("Error occurred on getting members of group `{}`: ", group, e);
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<?, ?> msg) {
        if (msg.message() instanceof GroupsClientMessage && msg.address() instanceof IdentityPublicKey) {
            final GroupsClientMessage grpMsg = (GroupsClientMessage) msg.message();

            if (grpMsg instanceof GroupJoinMessage) {
                ctx.executor().execute(() -> handleJoinRequest(ctx, (IdentityPublicKey) msg.address(), (GroupJoinMessage) grpMsg, new CompletableFuture<>()));
            }
            else if (grpMsg instanceof GroupLeaveMessage) {
                ctx.executor().execute(() -> handleLeaveRequest(ctx, (IdentityPublicKey) msg.address(), (GroupLeaveMessage) grpMsg, new CompletableFuture<>()));
            }
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Handles a join request of the given {@code sender}.
     *
     * @param ctx    the handler context
     * @param sender the sender of the join request
     * @param msg    the join request message
     * @param future the message future
     */
    private void handleJoinRequest(final ChannelHandlerContext ctx,
                                   final IdentityPublicKey sender,
                                   final GroupJoinMessage msg,
                                   final CompletableFuture<Void> future) {
        final String groupName = msg.getGroup().getName();
        try {
            final Group group = database.getGroup(groupName);

            if (group != null) {
                if (msg.getProofOfWork().isValid(sender, group.getMinDifficulty())) {
                    doJoin(ctx, sender, group, future, msg.isRenew());
                }
                else {
                    FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(groupName), ERROR_PROOF_TO_WEAK), sender)))).combine(new CompletableFuture<>());
                    future.completeExceptionally(new IllegalArgumentException("Member '" + sender + "' does not fulfill requirements of group '" + groupName + "'"));

                    LOG.debug("Member `{}` does not fulfill requirements of group `{}`", sender, groupName);
                }
            }
            else {
                FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(groupName), ERROR_GROUP_NOT_FOUND), sender)))).combine(new CompletableFuture<>());
                future.completeExceptionally(new IllegalArgumentException("There is no group '" + groupName + "'"));

                LOG.debug("There is no group `{}`.", groupName);
            }
        }
        catch (final DatabaseException e) {
            future.completeExceptionally(e);

            LOG.debug("Error occurred on getting group `{}`: ", groupName, e);
        }
    }

    /**
     * Handles a leave request of the given {@code sender}.
     *
     * @param ctx    the handler context
     * @param sender the sender of the leave request
     * @param msg    the leave request
     * @param future the message future
     */
    private void handleLeaveRequest(final ChannelHandlerContext ctx,
                                    final IdentityPublicKey sender,
                                    final GroupLeaveMessage msg,
                                    final CompletableFuture<Void> future) {
        try {
            final MemberLeftMessage leftMessage = new MemberLeftMessage(sender, msg.getGroup());

            database.removeGroupMember(sender, msg.getGroup().getName());
            final CompletableFuture<Void> future3 = new CompletableFuture<>();
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(leftMessage, sender)))).combine(future3);
            final CompletableFuture<Void> future1 = future3;
            final CompletableFuture<Void> future2 = new CompletableFuture<>();
            notifyMembers(ctx, msg.getGroup().getName(), leftMessage, future2);

            FutureCombiner.getInstance().addAll(future1, future2).combine(future);
            LOG.debug("Removed member `{}` from group `{}`", () -> sender, () -> msg.getGroup().getName());
        }
        catch (final DatabaseException e) {
            future.completeExceptionally(e);

            LOG.debug("Error occurred during removal of member `{}` from group `{}`: ", () -> sender, () -> msg.getGroup().getName(), () -> e);
        }
    }

    /**
     * This message does the actual join state transition.
     *
     * @param ctx     the handler context
     * @param sender  the sender
     * @param group   the group to join
     * @param future  the message future
     * @param isRenew if this is a renew
     */
    private void doJoin(final ChannelHandlerContext ctx,
                        final IdentityPublicKey sender,
                        final Group group,
                        final CompletableFuture<Void> future,
                        final boolean isRenew) {
        try {
            if (database.addGroupMember(
                    Membership.of(
                            Member.of(sender),
                            group,
                            System.currentTimeMillis() + group.getTimeout().toMillis())) || !isRenew) {
                final Set<IdentityPublicKey> memberships = database.getGroupMembers(group.getName()).stream()
                        .sequential()
                        .map(v -> v.getMember().getPublicKey())
                        .collect(Collectors.toSet());
                final CompletableFuture<Void> future3 = new CompletableFuture<>();
                FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(new GroupWelcomeMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), memberships), sender)))).combine(future3);
                final CompletableFuture<Void> future1 = future3;
                final CompletableFuture<Void> future2 = new CompletableFuture<>();
                FutureCombiner.getInstance().addAll(future1, future2).combine(future);

                notifyMembers(ctx, group.getName(), new MemberJoinedMessage(sender, org.drasyl.plugin.groups.client.Group.of(group.getName())), future2);

                LOG.debug("Added member `{}` to group `{}`", () -> sender, group::getName);
            }
            else {
                LOG.debug("Renewed membership of `{}` for group `{}`", () -> sender, group::getName);
            }
        }
        catch (final DatabaseException e) {
            FutureCombiner.getInstance().add(FutureUtil.toFuture(ctx.writeAndFlush(new AddressedMessage<>(new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_UNKNOWN), sender)))).combine(new CompletableFuture<>());
            future.completeExceptionally(e);

            LOG.debug("Error occurred during join: ", e);
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().attr(INBOUND_SERIALIZATION_ATTR_KEY).get().addSerializer(GroupsClientMessage.class, new JacksonJsonSerializer());
        ctx.channel().attr(OUTBOUND_SERIALIZATION_ATTR_KEY).get().addSerializer(GroupsServerMessage.class, new JacksonJsonSerializer());

        // Register stale task timer
        staleTask = ctx.executor().scheduleAtFixedRate(() -> staleTask(ctx), 1L, 1L, MINUTES);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        if (staleTask != null) {
            staleTask.cancel(false);
        }
    }
}
