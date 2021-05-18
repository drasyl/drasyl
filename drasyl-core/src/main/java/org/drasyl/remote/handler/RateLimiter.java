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
package org.drasyl.remote.handler;

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.MessageLite;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.handler.InboundMessageFilter;
import org.drasyl.remote.protocol.InvalidMessageFormatException;
import org.drasyl.remote.protocol.Protocol.MessageType;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.drasyl.util.DurationUtil.max;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler rate limits {@link org.drasyl.remote.protocol.Protocol.Acknowledgement}, {@link
 * org.drasyl.remote.protocol.Protocol.Discovery}, and {@link org.drasyl.remote.protocol.Protocol.Unite}
 * messages addressed to us. 1 message per type per sender per 100ms is allowed. Messages exceeding
 * the rate limit are dropped.
 */
@SuppressWarnings("java:S110")
public class RateLimiter extends InboundMessageFilter<RemoteEnvelope<? extends MessageLite>, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);
    private static final long CACHE_SIZE = 1_000;
    private static final long ACKNOWLEDGEMENT_RATE_LIMIT = 100; // 1 ack msg per 100ms
    private static final long DISCOVERY_RATE_LIMIT = 100; // 1 discovery msg per 100ms
    private static final long UNITE_RATE_LIMIT = 100; // 1 unit msg per 100ms
    private final Supplier<Long> timeProvider;
    private final ConcurrentMap<Pair<MessageType, IdentityPublicKey>, Long> cache;

    RateLimiter(final Supplier<Long> timeProvider,
                final ConcurrentMap<Pair<MessageType, IdentityPublicKey>, Long> cache) {
        this.timeProvider = requireNonNull(timeProvider);
        this.cache = requireNonNull(cache);
    }

    public RateLimiter() {
        this(
                System::currentTimeMillis,
                CacheBuilder.newBuilder()
                        .maximumSize(CACHE_SIZE)
                        .expireAfterAccess(max(max(ofMillis(ACKNOWLEDGEMENT_RATE_LIMIT), ofMillis(DISCOVERY_RATE_LIMIT)), ofMillis(UNITE_RATE_LIMIT)))
                        .<Pair<MessageType, IdentityPublicKey>, Long>build()
                        .asMap()
        );
    }

    @Override
    protected boolean accept(final HandlerContext ctx,
                             final Address sender,
                             final RemoteEnvelope<? extends MessageLite> msg) throws Exception {
        return !ctx.identity().getIdentityPublicKey().equals(msg.getRecipient()) || rateLimitGate(msg);
    }

    @Override
    protected void messageRejected(final HandlerContext ctx,
                                   final Address sender,
                                   final RemoteEnvelope<? extends MessageLite> msg,
                                   final CompletableFuture<Void> future) {
        LOG.trace("Message exceeding rate limit dropped: {}", () -> sanitizeLogArg(msg));
        future.completeExceptionally(new Exception("Message exceeding rate limit dropped"));
    }

    @SuppressWarnings("java:S1142")
    private boolean rateLimitGate(final RemoteEnvelope<? extends MessageLite> msg) throws InvalidMessageFormatException {
        switch (msg.getPrivateHeader().getType()) {
            case ACKNOWLEDGEMENT:
                return rateLimitMessage(msg.getSender(), ACKNOWLEDGEMENT, ACKNOWLEDGEMENT_RATE_LIMIT);
            case DISCOVERY:
                return rateLimitMessage(msg.getSender(), DISCOVERY, DISCOVERY_RATE_LIMIT);
            case UNITE:
                return rateLimitMessage(msg.getSender(), UNITE, UNITE_RATE_LIMIT);
            default:
                return true;
        }
    }

    private boolean rateLimitMessage(final IdentityPublicKey sender,
                                     final MessageType type,
                                     final long rateLimit) {
        final Pair<MessageType, IdentityPublicKey> key = Pair.of(type, sender);
        final Long lastReceived = cache.get(key);
        final long now = timeProvider.get();
        if (lastReceived == null || (now - lastReceived) >= rateLimit) {
            cache.put(key, now);
            return true;
        }
        return false;
    }
}
