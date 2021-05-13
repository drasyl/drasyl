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

import com.google.auto.value.AutoValue;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * This object represents a session key agreement between two nodes.
 */
@AutoValue
public abstract class Agreement {
    public static final long RENEW_DIVISOR = 2;
    /**
     * Default time to safely not return be stale.
     */
    public static final Duration DEFAULT_ON_ELSE_TIME = Duration.ofMinutes(60);

    static Builder builder() {
        return new AutoValue_Agreement.Builder();
    }

    public abstract KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> getKeyPair();

    public abstract Optional<KeyAgreementPublicKey> getRecipientsKeyAgreementKey();

    public abstract Optional<AgreementId> getAgreementId();

    public abstract Optional<SessionPair> getSessionPair();

    public abstract OptionalLong getStaleAt();

    public abstract Builder toBuilder();

    public boolean isStale() {
        return getStaleAt().orElse(System.currentTimeMillis() + DEFAULT_ON_ELSE_TIME.toMillis()) < System.currentTimeMillis();
    }

    public boolean isRenewable() {
        return getStaleAt().orElse(System.currentTimeMillis() + DEFAULT_ON_ELSE_TIME.toMillis()) < (System.currentTimeMillis() / RENEW_DIVISOR);
    }

    public boolean isInitialized() {
        return getSessionPair().isPresent() && getAgreementId().isPresent();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setKeyPair(KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair);

        abstract Builder setRecipientsKeyAgreementKey(Optional<KeyAgreementPublicKey> publicKey);

        abstract Builder setSessionPair(Optional<SessionPair> sessionPair);

        abstract Builder setAgreementId(Optional<AgreementId> agreementId);

        abstract Builder setStaleAt(OptionalLong staleAt);

        abstract Agreement build();
    }
}
