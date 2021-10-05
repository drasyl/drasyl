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

import com.google.auto.value.AutoValue;
import com.goterl.lazysodium.utils.SessionPair;

/**
 * This object represents a session key agreement between two nodes.
 */
@AutoValue
public abstract class Agreement {
    public static final long RENEW_DIVISOR = 2;

    public abstract AgreementId getAgreementId();

    public abstract SessionPair getSessionPair();

    /**
     * @return negative value means no stale (only for long time agreement)
     */
    public abstract long getStaleAt();

    public boolean isStale() {
        return getStaleAt() < 0 || getStaleAt() < System.currentTimeMillis();
    }

    public boolean isRenewable() {
        return getStaleAt() < 0 || getStaleAt() < (System.currentTimeMillis() / RENEW_DIVISOR);
    }

    public static Agreement of(final AgreementId id,
                               final SessionPair sessionPair,
                               final long staleAt) {
        return new AutoValue_Agreement(id, sessionPair, staleAt);
    }
}
