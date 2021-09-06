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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.plugin.groups.client.event.GroupJoinFailedEvent;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupLeftEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberLeftEvent;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GroupsClientHandler extends SimpleChannelInboundHandler<AddressedMessage<GroupsServerMessage, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientHandler.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration FIRST_JOIN_DELAY = Duration.ofSeconds(5);
    private final Duration firstJoinDelay;
    private final Map<Group, GroupUri> groups;
    private final Map<Group, Future<?>> renewTasks;
    private final Identity identity;

    @SuppressWarnings("java:S2384")
    GroupsClientHandler(final Map<Group, GroupUri> groups,
                        final Map<Group, Future<?>> renewTasks,
                        final Duration firstJoinDelay,
                        final Identity identity) {
        super(false);
        this.groups = requireNonNull(groups);
        this.renewTasks = requireNonNull(renewTasks);
        this.firstJoinDelay = requireNonNull(firstJoinDelay);
        this.identity = requireNonNull(identity);
    }

    public GroupsClientHandler(final Set<GroupUri> groups,
                               final Identity identity) {
        this(groups.stream().collect(Collectors.toMap(GroupUri::getGroup, groupURI -> groupURI)),
                new ConcurrentHashMap<>(), FIRST_JOIN_DELAY, identity);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof GroupsServerMessage;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<GroupsServerMessage, ?> msg) {
        final GroupsServerMessage grpMsg = msg.message();

        if (grpMsg instanceof MemberJoinedMessage) {
            onMemberJoined(ctx, (MemberJoinedMessage) grpMsg);
        }
        else if (grpMsg instanceof MemberLeftMessage) {
            onMemberLeft(ctx, (MemberLeftMessage) grpMsg);
        }
        else if (grpMsg instanceof GroupWelcomeMessage) {
            onWelcome(ctx, msg.address(), (GroupWelcomeMessage) grpMsg);
        }
        else if (grpMsg instanceof GroupJoinFailedMessage) {
            onJoinFailed(ctx, (GroupJoinFailedMessage) grpMsg);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        // Stop all renew tasks
        for (final Future<?> renewTask : renewTasks.values()) {
            renewTask.cancel(false);
        }
        renewTasks.clear();

        /*
         * Leave all groups.
         * This should be a blocking operation, so drasyl is not shutting down the communication
         * channel before the leave messages are sent.
         */
        for (final Entry<Group, GroupUri> entry : groups.entrySet()) {
            final Group group = entry.getKey();
            final GroupUri groupURI = entry.getValue();
            ctx.writeAndFlush(new AddressedMessage<>(new GroupLeaveMessage(group), groupURI.getManager())).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("Unable to send GroupLeaveMessage", future::cause);
                }
            });
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        // join every group but we will wait 5 seconds, to give it the chance to connect to some super peer if needed
        ctx.executor().schedule(() -> groups.values().forEach(group ->
                joinGroup(ctx, group, false)), firstJoinDelay.toMillis(), MILLISECONDS);

        ctx.fireChannelActive();
    }

    /**
     * Will be executed on {@link MemberJoinedMessage}.
     *
     * @param ctx the handling context
     * @param msg the member joined message
     */
    private static void onMemberJoined(final ChannelHandlerContext ctx,
                                       final MemberJoinedMessage msg) {
        ctx.fireUserEventTriggered(GroupMemberJoinedEvent.of(msg.getMember(), msg.getGroup()));
    }

    /**
     * Will be executed on {@link GroupJoinFailedMessage}.
     *
     * @param ctx the handling context
     * @param msg the join failed message
     */
    private void onJoinFailed(final ChannelHandlerContext ctx,
                              final GroupJoinFailedMessage msg) {
        final Group group = msg.getGroup();

        // cancel renew task
        final Future<?> disposable = renewTasks.remove(group);
        if (disposable != null) {
            disposable.cancel(false);
        }

        ctx.fireUserEventTriggered(GroupJoinFailedEvent.of(group, msg.getReason(),
                () -> joinGroup(ctx, groups.get(group), false)));
    }

    /**
     * Will be executed on {@link MemberLeftMessage}.
     *
     * @param ctx the handling context
     * @param msg the member left message
     */
    private void onMemberLeft(final ChannelHandlerContext ctx,
                              final MemberLeftMessage msg) {
        final Group group = msg.getGroup();

        if (msg.getMember().equals(identity.getIdentityPublicKey())) {
            // cancel renew task
            final Future<?> disposable = renewTasks.remove(group);
            if (disposable != null) {
                disposable.cancel(false);
            }

            ctx.fireUserEventTriggered(GroupLeftEvent.of(group, () -> joinGroup(ctx, groups.get(group), false)));
        }
        else {
            ctx.fireUserEventTriggered(GroupMemberLeftEvent.of(msg.getMember(), group));
        }
    }

    /**
     * Will be executed on {@link GroupWelcomeMessage}.
     *
     * @param ctx    the handling context
     * @param sender the sender (group manager)
     * @param msg    the welcome message
     */
    private void onWelcome(final ChannelHandlerContext ctx,
                           final SocketAddress sender,
                           final GroupWelcomeMessage msg) {
        final Group group = msg.getGroup();
        final Duration timeout = groups.get(group).getTimeout();

        // replace old re-try task with renew task
        final Future<?> disposable = renewTasks.remove(group);
        if (disposable != null) {
            disposable.cancel(false);
        }

        // Add renew task
        renewTasks.put(group, ctx.executor().scheduleWithFixedDelay(() ->
                joinGroup(ctx, groups.get(group), true), timeout.dividedBy(2).toMillis(), timeout.dividedBy(2).toMillis(), MILLISECONDS));

        ctx.fireUserEventTriggered(GroupJoinedEvent.of(
                group,
                msg.getMembers(),
                () -> ctx.writeAndFlush(new AddressedMessage<>(new GroupLeaveMessage(group), sender)).addListener(future -> {
                    if (!future.isSuccess()) {
                        LOG.warn("Unable to send GroupLeaveMessage", future::cause);
                    }
                })));
    }

    /**
     * Joins the given {@code group}.
     *
     * @param ctx   the handler context
     * @param group the group to join
     * @param renew if this is a renew message or not
     */
    private void joinGroup(final ChannelHandlerContext ctx,
                           final GroupUri group,
                           final boolean renew) {
        final ProofOfWork proofOfWork = identity.getProofOfWork();
        final IdentityPublicKey groupManager = group.getManager();

        ctx.writeAndFlush(new AddressedMessage<>(new GroupJoinMessage(group.getGroup(), group.getCredentials(), proofOfWork, renew), groupManager)).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.warn("Unable to send GroupJoinMessage", future::cause);
            }
        });

        // Add re-try task
        if (!renewTasks.containsKey(group.getGroup())) {
            renewTasks.put(group.getGroup(), ctx.executor().scheduleWithFixedDelay(() ->
                    joinGroup(ctx, groups.get(group.getGroup()), false), RETRY_DELAY.toMillis(), RETRY_DELAY.toMillis(), MILLISECONDS));
        }

        LOG.debug("Send join (renew={}) request for group `{}`", () -> renew, () -> group);
    }
}
