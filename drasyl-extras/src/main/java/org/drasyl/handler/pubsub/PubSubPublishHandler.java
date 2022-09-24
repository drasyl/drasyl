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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;

/**
 * This handler sends {@link PubSubPublish} messages to the {@link #broker}.
 * <p>
 * If the broker not confirms withing {@link #publishTimeout}ms, the write {@link Promise} is
 * failed.
 */
public class PubSubPublishHandler extends ChannelDuplexHandler {
    public static final Duration DEFAULT_PUBLISH_TIMEOUT = Duration.ofMillis(5_000L);
    private static final Logger LOG = LoggerFactory.getLogger(PubSubPublishHandler.class);
    private final Duration publishTimeout;
    private final Map<UUID, Promise<Void>> requests;
    private final DrasylAddress broker;

    PubSubPublishHandler(final Duration publishTimeout,
                         final Map<UUID, Promise<Void>> requests,
                         final DrasylAddress broker) {
        this.publishTimeout = requireNonNegative(publishTimeout);
        this.requests = requireNonNull(requests);
        this.broker = requireNonNull(broker);
    }

    public PubSubPublishHandler(final Duration publishTimeout, final DrasylAddress broker) {
        this(publishTimeout, new HashMap<>(), broker);
    }

    public PubSubPublishHandler(final DrasylAddress broker) {
        this(DEFAULT_PUBLISH_TIMEOUT, broker);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) {
        if (msg instanceof PubSubPublish) {
            doPublish(ctx, (PubSubPublish) msg, promise);
        }
        else {
            ctx.write(msg, promise);
        }
    }

    private void doPublish(final ChannelHandlerContext ctx,
                           final PubSubPublish msg,
                           final ChannelPromise promise) {
        LOG.trace("Send `{}` to broker `{}`.", msg, broker);
        ctx.write(new OverlayAddressedMessage<>(msg, broker)).addListener((FutureListener<Void>) future -> {
            if (!publishTimeout.isZero() && future.isSuccess()) {
                // create timeout guard
                requests.put(msg.getId(), promise);
                promise.addListener((FutureListener<Void>) future1 -> requests.remove(msg.getId()));
                ctx.executor().schedule(() -> promise.tryFailure(new Exception("Got no confirmation from broker within " + publishTimeout.toMillis() + "ms.")), publishTimeout.toMillis(), MILLISECONDS);
            }
            else {
                PromiseNotifier.cascade(future, promise);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof OverlayAddressedMessage && ((OverlayAddressedMessage<?>) msg).content() instanceof PubSubPublished && broker.equals(((OverlayAddressedMessage<PubSubPublished>) msg).sender())) {
            handlePublished((PubSubPublished) ((OverlayAddressedMessage<?>) msg).content());
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handlePublished(final PubSubPublished msg) {
        LOG.trace("Got `{}` from broker `{}`.", msg, broker);
        final Promise<Void> promise = requests.remove(msg.getId());
        if (promise != null) {
            promise.trySuccess(null);
        }
    }
}
