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
package org.drasyl.remote.handler.crypto;

import com.google.common.cache.CacheBuilder;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.util.ConcurrentReference;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class holds the current (long time/session | active/inactive/stale) {@link Agreement
 * Agreements} between two nodes.
 */
public class Session {
    public static final Duration SESSION_EXPIRE_TIME = Duration.ofMinutes(30);
    private final AgreementId longTimeAgreementId;
    private final AtomicLong lastKeyExchangeAt;
    private final AtomicLong lastRenewAttemptAt;
    private final SessionPair longTimeAgreementPair;
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
    private final ConcurrentReference<Agreement> currentInactiveAgreement;

    public Session(final AgreementId longTimeAgreementId,
                   final SessionPair longTimeAgreementPair,
                   final ConcurrentReference<Agreement> currentInactiveAgreement) {
        this.longTimeAgreementId = Objects.requireNonNull(longTimeAgreementId);
        this.lastKeyExchangeAt = new AtomicLong();
        this.lastRenewAttemptAt = new AtomicLong();
        this.longTimeAgreementPair = Objects.requireNonNull(longTimeAgreementPair);
        this.currentActiveAgreement = ConcurrentReference.of();
        this.currentInactiveAgreement = Objects.requireNonNull(currentInactiveAgreement);
        this.initializedAgreements = CacheBuilder.newBuilder()
                .expireAfterWrite(SESSION_EXPIRE_TIME.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(ArmHandler.MAX_AGREEMENTS)
                .<AgreementId, Agreement>build()
                .asMap();
    }

    public Session(final AgreementId longTimeAgreementId, final SessionPair longTimeAgreementPair) {
        this(longTimeAgreementId, longTimeAgreementPair, ConcurrentReference.of());
    }

    public AgreementId getLongTimeAgreementId() {
        return longTimeAgreementId;
    }

    public AtomicLong getLastKeyExchangeAt() {
        return lastKeyExchangeAt;
    }

    public AtomicLong getLastRenewAttemptAt() {
        return lastRenewAttemptAt;
    }

    public SessionPair getLongTimeAgreementPair() {
        return longTimeAgreementPair;
    }

    public Map<AgreementId, Agreement> getInitializedAgreements() {
        return initializedAgreements;
    }

    public ConcurrentReference<Agreement> getCurrentActiveAgreement() {
        return currentActiveAgreement;
    }

    public ConcurrentReference<Agreement> getCurrentInactiveAgreement() {
        return currentInactiveAgreement;
    }
}
