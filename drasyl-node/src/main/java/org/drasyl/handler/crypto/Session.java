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
package org.drasyl.handler.crypto;

import com.google.common.cache.CacheBuilder;
import org.drasyl.util.ConcurrentReference;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This class holds the current (long time/session | active/inactive/stale) {@link Agreement
 * Agreements} between two nodes.
 */
public class Session {
    private final Agreement longTimeAgreement;
    /**
     * This map stores all fully initialized agreements. From time to time stale sessions get
     * dropped to save memory.
     */
    private final Map<AgreementId, Agreement> initializedAgreements;
    /**
     * This is the currently active agreement that has received the latest ACK. It will be used to
     * encrypt all messages. If this value is {@code null} the long time key will be used instead.
     */
    private final ConcurrentReference<Agreement> currentActiveAgreement;
    /**
     * This agreement waits for an ACK to be fully initialized and used. As long as this agreement
     * is not initialized, it can be used to answer all key agreement request of this specific
     * participant.
     */
    private final ConcurrentReference<PendingAgreement> currentInactiveAgreement;
    private long lastKeyExchangeAt;
    private long lastRenewAttemptAt;

    public Session(final Agreement longTimeAgreement,
                   final ConcurrentReference<PendingAgreement> currentInactiveAgreement,
                   final int maxAgreements,
                   final Duration sessionExpireTime) {
        this.longTimeAgreement = Objects.requireNonNull(longTimeAgreement);
        this.currentActiveAgreement = ConcurrentReference.of();
        this.currentInactiveAgreement = Objects.requireNonNull(currentInactiveAgreement);
        this.initializedAgreements = CacheBuilder.newBuilder()
                .expireAfterWrite(sessionExpireTime.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(maxAgreements)
                .<AgreementId, Agreement>build()
                .asMap();
    }

    public Session(final Agreement longTimeAgreement,
                   final int maxAgreements,
                   final Duration sessionExpireTime) {
        this(longTimeAgreement, ConcurrentReference.of(), maxAgreements, sessionExpireTime);
    }

    public Map<AgreementId, Agreement> getInitializedAgreements() {
        return initializedAgreements;
    }

    public ConcurrentReference<Agreement> getCurrentActiveAgreement() {
        return currentActiveAgreement;
    }

    public ConcurrentReference<PendingAgreement> getCurrentInactiveAgreement() {
        return currentInactiveAgreement;
    }

    public long getLastKeyExchangeAt() {
        return lastKeyExchangeAt;
    }

    public void setLastKeyExchangeAt(final long lastKeyExchangeAt) {
        this.lastKeyExchangeAt = lastKeyExchangeAt;
    }

    public long getLastRenewAttemptAt() {
        return lastRenewAttemptAt;
    }

    public void setLastRenewAttemptAt(final long lastRenewAttemptAt) {
        this.lastRenewAttemptAt = lastRenewAttemptAt;
    }

    public Agreement getLongTimeAgreement() {
        return longTimeAgreement;
    }
}
