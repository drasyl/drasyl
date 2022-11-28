/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.pubsub;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.HashSetMultimap;
import org.drasyl.util.Multimap;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * This handler handles subscribers and publications.
 */
public class PubSubBrokerHandler extends SimpleChannelInboundHandler<OverlayAddressedMessage<PubSubMessage>> {
    private static final Logger LOG = LoggerFactory.getLogger(PubSubBrokerHandler.class);
    private final Multimap<String, DrasylAddress> subscriptions;

    PubSubBrokerHandler(final Multimap<String, DrasylAddress> subscriptions) {
        this.subscriptions = requireNonNull(subscriptions);
    }

    public PubSubBrokerHandler() {
        this(new HashSetMultimap<>());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        // inform all subscribers that we're shutting down
        for (final String topic : subscriptions.keySet()) {
            final Collection<DrasylAddress> subscribers = subscriptions.get(topic);
            for (final DrasylAddress subscriber : subscribers) {
                ctx.writeAndFlush(new OverlayAddressedMessage<>(PubSubUnsubscribe.of(topic), subscriber));
            }
        }
        if (!subscriptions.isEmpty()) {
            ctx.flush();
            subscriptions.clear();
        }

        ctx.fireChannelInactive();
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {
        return msg instanceof OverlayAddressedMessage && (((OverlayAddressedMessage<?>) msg).content() instanceof PubSubPublish || ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubSubscribe || ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubUnsubscribe);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<PubSubMessage> msg) throws Exception {
        LOG.trace("Got `{}` from `{}`.", msg.content(), msg.sender());
        if (msg.content() instanceof PubSubPublish) {
            handlePublish(ctx, (PubSubPublish) msg.content(), msg.sender());
        }
        else if (msg.content() instanceof PubSubSubscribe) {
            handleSubscribe(ctx, (PubSubSubscribe) msg.content(), msg.sender());
        }
        else if (msg.content() instanceof PubSubUnsubscribe) {
            handleUnsubscribe(ctx, (PubSubUnsubscribe) msg.content(), msg.sender());
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePublish(final ChannelHandlerContext ctx,
                               final PubSubPublish msg,
                               final DrasylAddress sender) {
        final Collection<DrasylAddress> subscribers = subscriptions.get(msg.getTopic());
        if (subscribers.isEmpty()) {
            LOG.debug("Topic `{}` got new publication from `{}`. But as there are no subscribers, publication is dropped.", msg::getTopic, () -> sender);
        }
        else {
            LOG.debug("Topic `{}` got new publication from `{}`. Forward to {} subscriber(s).", msg::getTopic, () -> sender, subscribers::size);
            for (final DrasylAddress subscriber : subscribers) {
                ctx.writeAndFlush(new OverlayAddressedMessage<>(msg.retain(), subscriber));
            }
        }

        // send confirmation
        ctx.writeAndFlush(new OverlayAddressedMessage<>(PubSubPublished.of(msg.getId()), sender));
    }

    private void handleSubscribe(final ChannelHandlerContext ctx,
                                 final PubSubSubscribe msg,
                                 final DrasylAddress sender) {
        if (subscriptions.put(msg.getTopic(), sender)) {
            LOG.debug("Topic `{}` got new subscriber: `{}`", msg.getTopic(), sender);
        }

        // send confirmation
        ctx.writeAndFlush(new OverlayAddressedMessage<>(PubSubSubscribed.of(msg.getId()), sender));
    }

    private void handleUnsubscribe(final ChannelHandlerContext ctx,
                                   final PubSubUnsubscribe msg,
                                   final DrasylAddress sender) {
        if (subscriptions.remove(msg.getTopic(), sender)) {
            LOG.debug("Topic `{}` lost subscriber: `{}`", msg.getTopic(), sender);
        }

        // send confirmation
        ctx.writeAndFlush(new OverlayAddressedMessage<>(PubSubUnsubscribed.of(msg.getId()), sender));
    }
}
