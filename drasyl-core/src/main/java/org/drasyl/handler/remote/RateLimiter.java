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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.channel.InetAddressedMessage;
import org.drasyl.handler.remote.protocol.AcknowledgementMessage;
import org.drasyl.handler.remote.protocol.FullReadMessage;
import org.drasyl.handler.remote.protocol.HelloMessage;
import org.drasyl.handler.remote.protocol.UniteMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.ExpiringMap;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

import static java.lang.Long.max;
import static java.util.Objects.requireNonNull;

/**
 * This handler rate limits {@link AcknowledgementMessage}, {@link HelloMessage}, and {@link
 * UniteMessage} messages addressed to us. 1 message per type per sender per 100ms is allowed.
 * Messages exceeding the rate limit are dropped.
 */
@SuppressWarnings("java:S110")
public class RateLimiter extends SimpleChannelInboundHandler<InetAddressedMessage<FullReadMessage<?>>> {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);
    private static final long CACHE_SIZE = 1_000;
    private static final long ACKNOWLEDGEMENT_RATE_LIMIT = 100; // 1 ack msg per 100ms
    private static final long HELLO_RATE_LIMIT = 100; // 1 hello msg per 100ms
    private static final long UNITE_RATE_LIMIT = 100; // 1 unit msg per 100ms
    private final Supplier<Long> timeProvider;
    private final Map<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache;

    RateLimiter(final Supplier<Long> timeProvider,
                final Map<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long> cache) {
        super(false);
        this.timeProvider = requireNonNull(timeProvider);
        this.cache = requireNonNull(cache);
    }

    public RateLimiter() {
        this(
                System::currentTimeMillis,
                new ExpiringMap<Pair<? extends Class<? extends FullReadMessage<?>>, DrasylAddress>, Long>(CACHE_SIZE, -1, max(max(ACKNOWLEDGEMENT_RATE_LIMIT, HELLO_RATE_LIMIT), UNITE_RATE_LIMIT))
        );
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) {
        return msg instanceof InetAddressedMessage && ((InetAddressedMessage<?>) msg).content() instanceof FullReadMessage;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final InetAddressedMessage<FullReadMessage<?>> msg) {
        final FullReadMessage<?> fullReadMsg = msg.content();

        if (!ctx.channel().localAddress().equals(fullReadMsg.getRecipient()) || rateLimitGate(fullReadMsg)) {
            ctx.fireChannelRead(msg);
        }
        else {
            msg.release();
            LOG.debug("Message `{}` from `{}` exceeding rate limit dropped.", fullReadMsg::getNonce, msg::sender);
        }
    }

    @SuppressWarnings({ "java:S1142", "unchecked" })
    private boolean rateLimitGate(final FullReadMessage<?> msg) {
        if (msg instanceof AcknowledgementMessage) {
            return rateLimitMessage(msg.getSender(), (Class<? extends FullReadMessage<?>>) msg.getClass(), ACKNOWLEDGEMENT_RATE_LIMIT);
        }
        else if (msg instanceof HelloMessage) {
            return rateLimitMessage(msg.getSender(), (Class<? extends FullReadMessage<?>>) msg.getClass(), HELLO_RATE_LIMIT);
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
