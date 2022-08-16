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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;

@SuppressWarnings({ "java:S1192", "DuplicatedCode" })
public class PubSubSubscribeHandler extends ChannelDuplexHandler {
    public static final long DEFAULT_SUBSCRIBE_TIMEOUT = 5_000L;
    private static final Logger LOG = LoggerFactory.getLogger(PubSubSubscribeHandler.class);
    private final long subscribeTimeout;
    private final Map<UUID, Pair<Promise<Void>, String>> requests;
    private final DrasylAddress broker;
    private final Set<String> subscriptions;

    PubSubSubscribeHandler(final long subscribeTimeout,
                           final Map<UUID, Pair<Promise<Void>, String>> requests,
                           final DrasylAddress broker,
                           final Set<String> subscriptions) {
        this.subscribeTimeout = requireNonNegative(subscribeTimeout);
        this.requests = requireNonNull(requests);
        this.broker = requireNonNull(broker);
        this.subscriptions = requireNonNull(subscriptions);
    }

    public PubSubSubscribeHandler(final long subscribeTimeout, final DrasylAddress broker) {
        this(subscribeTimeout, new HashMap<>(), broker, new HashSet<>());
    }

    public PubSubSubscribeHandler(final DrasylAddress broker) {
        this(DEFAULT_SUBSCRIBE_TIMEOUT, broker);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        unsubscribeFromAll(ctx);
        ctx.fireChannelInactive();
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof PubSubSubscribe) {
            doSubscribe(ctx, (PubSubSubscribe) msg, promise);
        }
        else if (msg instanceof PubSubUnsubscribe) {
            doUnsubscribe(ctx, (PubSubUnsubscribe) msg, promise);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void doSubscribe(final ChannelHandlerContext ctx,
                             final PubSubSubscribe msg,
                             final ChannelPromise promise) {
        LOG.trace("Send `{}` to broker `{}`.", msg, broker);
        ctx.write(new OverlayAddressedMessage<>(msg, broker)).addListener((FutureListener<Void>) future -> {
            if (subscribeTimeout > 0 && future.isSuccess()) {
                // create timeout guard
                requests.put(msg.getId(), Pair.of(promise, msg.getTopic()));
                promise.addListener((FutureListener<Void>) future1 -> requests.remove(msg.getId()));
                ctx.executor().schedule(() -> promise.tryFailure(new Exception("Got no confirmation from broker within " + subscribeTimeout + "ms.")), subscribeTimeout, MILLISECONDS);
            }
            else {
                PromiseNotifier.cascade(future, promise);
            }
        });
    }

    private void doUnsubscribe(final ChannelHandlerContext ctx,
                               final PubSubUnsubscribe msg,
                               final ChannelPromise promise) {
        LOG.trace("Send `{}` to broker `{}`.", msg, broker);
        ctx.write(new OverlayAddressedMessage<>(msg, broker)).addListener((FutureListener<Void>) future -> {
            if (subscribeTimeout > 0 && future.isSuccess()) {
                // create timeout guard
                requests.put(msg.getId(), Pair.of(promise, msg.getTopic()));
                promise.addListener((FutureListener<Void>) future1 -> requests.remove(msg.getId()));
                ctx.executor().schedule(() -> promise.tryFailure(new Exception("Got no confirmation from broker within " + subscribeTimeout + "ms.")), subscribeTimeout, MILLISECONDS);
            }
            else {
                PromiseNotifier.cascade(future, promise);
            }
        });
    }

    @SuppressWarnings({ "unchecked", "java:S1541" })
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubSubscribed && broker.equals(((OverlayAddressedMessage<PubSubMessage>) msg).sender())) {
            handleSubscribed((PubSubSubscribed) ((OverlayAddressedMessage<?>) msg).content());
        }
        else if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubUnsubscribed && broker.equals(((OverlayAddressedMessage<PubSubMessage>) msg).sender())) {
            handleUnsubscribed((PubSubUnsubscribed) ((OverlayAddressedMessage<?>) msg).content());
        }
        else if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubPublish && broker.equals(((OverlayAddressedMessage<PubSubMessage>) msg).sender())) {
            handlePublish(ctx, (PubSubPublish) ((OverlayAddressedMessage<?>) msg).content());
        }
        else if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubUnsubscribe && broker.equals(((OverlayAddressedMessage<PubSubMessage>) msg).sender())) {
            handleUnsubscribe((PubSubUnsubscribe) ((OverlayAddressedMessage<?>) msg).content());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleSubscribed(final PubSubSubscribed msg) {
        LOG.trace("Got `{}` from broker `{}`.", msg, broker);
        final Pair<Promise<Void>, String> pair = requests.remove(msg.getId());
        if (pair != null) {
            final Promise<Void> promise = pair.first();
            final String topic = pair.second();
            if (subscriptions.add(topic)) {
                LOG.debug("Subscribed to topic `{}` at broker `{}`.", topic, broker);
            }
            promise.trySuccess(null);
        }
    }

    private void handleUnsubscribed(final PubSubUnsubscribed msg) {
        LOG.trace("Got `{}` from broker `{}`.", msg, broker);
        final Pair<Promise<Void>, String> pair = requests.remove(msg.getId());
        if (pair != null) {
            final Promise<Void> promise = pair.first();
            final String topic = pair.second();
            if (subscriptions.remove(topic)) {
                LOG.debug("Unsubscribed from topic `{}` from broker `{}`.", topic, broker);
            }
            promise.trySuccess(null);
        }
    }

    private void handlePublish(final ChannelHandlerContext ctx, final PubSubPublish msg) {
        LOG.trace("Got `{}` from broker `{}`.", msg, broker);
        final String topic = msg.getTopic();
        if (subscriptions.contains(topic)) {
            LOG.trace("Got publication for topic `{}` from broker `{}`: {}", topic, broker, msg.getContent());
            ctx.fireChannelRead(msg);
        }
        else {
            LOG.trace("Got publication for topic `{}` from broker `{}` we're not subscribed to. Discard publication: {}", topic, broker, msg.getContent());
            msg.getContent().release();
        }
    }

    private void handleUnsubscribe(final PubSubUnsubscribe msg) {
        LOG.trace("Got `{}` from broker `{}`.", msg, broker);
        final String topic = msg.getTopic();
        if (subscriptions.remove(topic)) {
            LOG.debug("Unsubscribed from topic `{}` as broker `{}` is shutting down.", topic, broker);
        }
    }

    private void unsubscribeFromAll(final ChannelHandlerContext ctx) {
        for (final String topic : subscriptions) {
            LOG.trace("Channel is closing. Unsubscribe from topic `{}` at broker `{}`.", topic, broker);
            ctx.write(new OverlayAddressedMessage<>(PubSubUnsubscribe.of(topic), broker));
        }
        if (!subscriptions.isEmpty()) {
            ctx.flush();
            subscriptions.clear();
        }
    }
}
