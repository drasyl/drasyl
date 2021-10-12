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
package org.drasyl.handler.remote;

import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.DiscoveryMessage;
import org.drasyl.handler.remote.protocol.FullReadMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;
import static org.drasyl.util.DurationUtil.max;

/**
 * This handler rate limits {@link AcknowledgementMessage}, {@link DiscoveryMessage}, and {@link
 * UniteMessage} messages addressed to us. 1 message per type per sender per 100ms is allowed.
 * Messages exceeding the rate limit are dropped.
 */
@SuppressWarnings("java:S110")
public class RateLimiter extends SimpleChannelInboundHandler<AddressedMessage<FullReadMessage<?>, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);
    private static final long CACHE_SIZE = 1_000;
    private static final long ACKNOWLEDGEMENT_RATE_LIMIT = 100; // 1 ack msg per 100ms
    private static final long DISCOVERY_RATE_LIMIT = 100; // 1 discovery msg per 100ms
    private static final long UNITE_RATE_LIMIT = 100; // 1 unit msg per 100ms
    private final Supplier<Long> timeProvider;
    private final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache;

    RateLimiter(final Supplier<Long> timeProvider,
                final ConcurrentMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache) {
        super(false);
        this.timeProvider = requireNonNull(timeProvider);
        this.cache = requireNonNull(cache);
    }

    public RateLimiter() {
        this(
                System::currentTimeMillis,
                CacheBuilder.newBuilder()
                        .maximumSize(CACHE_SIZE)
                        .expireAfterAccess(max(max(ofMillis(ACKNOWLEDGEMENT_RATE_LIMIT), ofMillis(DISCOVERY_RATE_LIMIT)), ofMillis(UNITE_RATE_LIMIT)))
                        .<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long>build()
                        .asMap()
        );
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof AddressedMessage && ((AddressedMessage<?, ?>) msg).message() instanceof FullReadMessage;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final AddressedMessage<FullReadMessage<?>, ?> msg) {
        final FullReadMessage<?> fullReadMsg = msg.message();

        if (!ctx.channel().localAddress().equals(fullReadMsg.getRecipient()) || rateLimitGate(fullReadMsg)) {
            ctx.fireChannelRead(msg);
        }
        else {
            msg.release();
            LOG.debug("Message `{}` exceeding rate limit dropped.", fullReadMsg::getNonce);
        }
    }

    @SuppressWarnings({ "java:S1142", "unchecked" })
    private boolean rateLimitGate(final FullReadMessage<?> msg) {
        if (msg instanceof AcknowledgementMessage) {
            return rateLimitMessage(msg.getSender(), (Class<? extends FullReadMessage<?>>) msg.getClass(), ACKNOWLEDGEMENT_RATE_LIMIT);
        }
        else if (msg instanceof DiscoveryMessage) {
            return rateLimitMessage(msg.getSender(), (Class<? extends FullReadMessage<?>>) msg.getClass(), DISCOVERY_RATE_LIMIT);
        }
        else if (msg instanceof UniteMessage) {
            return rateLimitMessage(msg.getSender(), (Class<? extends FullReadMessage<?>>) msg.getClass(), UNITE_RATE_LIMIT);
        }
        else {
            return true;
        }
    }

    private boolean rateLimitMessage(final DrasylAddress sender,
                                     final Class<? extends FullReadMessage<?>> type,
                                     final long rateLimit) {
        final Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress> key = Pair.of(type, sender);
        final Long lastReceived = cache.get(key);
        final long now = timeProvider.get();
        if (lastReceived == null || (now - lastReceived) >= rateLimit) {
            cache.put(key, now);
            return true;
        }
        return false;
    }
}
