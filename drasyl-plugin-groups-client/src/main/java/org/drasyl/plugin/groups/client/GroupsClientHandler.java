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
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundEventAwareHandler;
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
import org.drasyl.util.FutureCombiner;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GroupsClientHandler extends SimpleInboundEventAwareHandler<GroupsServerMessage, NodeUpEvent, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientHandler.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration FIRST_JOIN_DELAY = Duration.ofSeconds(5);
    private final Duration firstJoinDelay;
    private final Map<Group, GroupUri> groups;
    private final Map<Group, Disposable> renewTasks;

    @SuppressWarnings("java:S2384")
    GroupsClientHandler(final Map<Group, GroupUri> groups,
                        final Map<Group, Disposable> renewTasks,
                        final Duration firstJoinDelay) {
        this.groups = requireNonNull(groups);
        this.renewTasks = requireNonNull(renewTasks);
        this.firstJoinDelay = requireNonNull(firstJoinDelay);
    }

    public GroupsClientHandler(final Set<GroupUri> groups) {
        this(groups.stream().collect(Collectors.toMap(GroupUri::getGroup, groupURI -> groupURI)),
                new ConcurrentHashMap<>(), FIRST_JOIN_DELAY);
    }

    @Override
    public void onAdded(final HandlerContext ctx) {
        ctx.inboundSerialization().addSerializer(GroupsServerMessage.class, new JacksonJsonSerializer());
        ctx.outboundSerialization().addSerializer(GroupsClientMessage.class, new JacksonJsonSerializer());
    }

    @Override
    public void onRemoved(final HandlerContext ctx) {
        // Stop all renew tasks
        for (final Disposable renewTask : renewTasks.values()) {
            renewTask.dispose();
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
            try {
                ctx.pipeline().processOutbound(groupURI.getManager(), new GroupLeaveMessage(group)).get();
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Exception occurred during de-registration from group '{}': ", group.getName(), e);
            }
            catch (final ExecutionException e) {
                LOG.warn("Exception occurred during de-registration from group '{}': ", group.getName(), e);
            }
        }
    }

    @Override
    protected void matchedEvent(final HandlerContext ctx,
                                final NodeUpEvent event,
                                final CompletableFuture<Void> future) {
        // join every group but we will wait 5 seconds, to give it the chance to connect to some super peer if needed
        ctx.independentScheduler().scheduleDirect(() -> groups.values().forEach(group ->
                joinGroup(ctx, group, false)), firstJoinDelay.toMillis(), MILLISECONDS);

        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final GroupsServerMessage msg,
                                  final CompletableFuture<Void> future) {
        if (msg instanceof MemberJoinedMessage) {
            onMemberJoined(ctx, (MemberJoinedMessage) msg, future);
        }
        else if (msg instanceof MemberLeftMessage) {
            onMemberLeft(ctx, (MemberLeftMessage) msg, future);
        }
        else if (msg instanceof GroupWelcomeMessage) {
            onWelcome(ctx, sender, (GroupWelcomeMessage) msg, future);
        }
        else if (msg instanceof GroupJoinFailedMessage) {
            onJoinFailed(ctx, (GroupJoinFailedMessage) msg, future);
        }
    }

    /**
     * Will be executed on {@link MemberJoinedMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the member joined message
     * @param future the message future
     */
    private static void onMemberJoined(final HandlerContext ctx,
                                       final MemberJoinedMessage msg,
                                       final CompletableFuture<Void> future) {
        FutureCombiner.getInstance()
                .add(ctx.pipeline().processInbound(GroupMemberJoinedEvent.of(msg.getMember(), msg.getGroup())))
                .combine(future);
    }

    /**
     * Will be executed on {@link GroupJoinFailedMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the join failed message
     * @param future the message future
     */
    private void onJoinFailed(final HandlerContext ctx,
                              final GroupJoinFailedMessage msg,
                              final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();

        // cancel renew task
        final Disposable disposable = renewTasks.remove(group);
        if (disposable != null) {
            disposable.dispose();
        }

        FutureCombiner.getInstance()
                .add(ctx.pipeline().processInbound(GroupJoinFailedEvent.of(group, msg.getReason(),
                        () -> joinGroup(ctx, groups.get(group), false))))
                .combine(future);
    }

    /**
     * Will be executed on {@link MemberLeftMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the member left message
     * @param future the message future
     */
    private void onMemberLeft(final HandlerContext ctx,
                              final MemberLeftMessage msg,
                              final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();

        if (msg.getMember().equals(ctx.identity().getIdentityPublicKey())) {
            // cancel renew task
            final Disposable disposable = renewTasks.remove(group);
            if (disposable != null) {
                disposable.dispose();
            }

            FutureCombiner.getInstance()
                    .add(ctx.pipeline().processInbound(GroupLeftEvent.of(group, () -> joinGroup(ctx, groups.get(group), false))))
                    .combine(future);
        }
        else {
            FutureCombiner.getInstance()
                    .add(ctx.pipeline().processInbound(GroupMemberLeftEvent.of(msg.getMember(), group)))
                    .combine(future);
        }
    }

    /**
     * Will be executed on {@link GroupWelcomeMessage}.
     *
     * @param ctx    the handling context
     * @param sender the sender (group manager)
     * @param msg    the welcome message
     * @param future the message future
     */
    private void onWelcome(final HandlerContext ctx,
                           final Address sender,
                           final GroupWelcomeMessage msg,
                           final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();
        final Duration timeout = groups.get(group).getTimeout();

        // replace old re-try task with renew task
        final Disposable disposable = renewTasks.remove(group);
        if (disposable != null) {
            disposable.dispose();
        }

        // Add renew task
        renewTasks.put(group, ctx.independentScheduler().schedulePeriodicallyDirect(() ->
                        joinGroup(ctx, groups.get(group), true),
                timeout.dividedBy(2).toMillis(), timeout.dividedBy(2).toMillis(), MILLISECONDS));

        FutureCombiner.getInstance()
                .add(ctx.pipeline().processInbound(
                        GroupJoinedEvent.of(
                                group,
                                msg.getMembers(),
                                () -> ctx.pipeline().processOutbound(sender, new GroupLeaveMessage(group)))))
                .combine(future);
    }

    /**
     * Joins the given {@code group}.
     *
     * @param ctx   the handler context
     * @param group the group to join
     * @param renew if this is a renew message or not
     */
    private void joinGroup(final HandlerContext ctx,
                           final GroupUri group,
                           final boolean renew) {
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();
        final IdentityPublicKey groupManager = group.getManager();

        ctx.pipeline().processOutbound(groupManager, new GroupJoinMessage(group.getGroup(), group.getCredentials(), proofOfWork, renew));

        // Add re-try task
        if (!renewTasks.containsKey(group.getGroup())) {
            renewTasks.put(group.getGroup(), ctx.independentScheduler().schedulePeriodicallyDirect(() ->
                            joinGroup(ctx, groups.get(group.getGroup()), false),
                    RETRY_DELAY.toMillis(),
                    RETRY_DELAY.toMillis(), MILLISECONDS));
        }

        LOG.debug("Send join (renew={}) request for group '{}'", () -> renew, () -> group);
    }
}
