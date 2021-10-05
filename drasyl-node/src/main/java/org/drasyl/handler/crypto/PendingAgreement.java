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

import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;

import java.util.Objects;

public class PendingAgreement {
    private final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair;
    private KeyAgreementPublicKey recipientsKeyAgreementKey;
    private AgreementId agreementId;

    public PendingAgreement(final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> keyPair) {
        this.keyPair = keyPair;
    }

    public KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> getKeyPair() {
        return keyPair;
    }

    public KeyAgreementPublicKey getRecipientsKeyAgreementKey() {
        return recipientsKeyAgreementKey;
    }

    public void setRecipientsKeyAgreementKey(final KeyAgreementPublicKey recipientsKeyAgreementKey) {
        this.recipientsKeyAgreementKey = recipientsKeyAgreementKey;
        this.agreementId = AgreementId.of(keyPair.getPublicKey(), recipientsKeyAgreementKey);
    }

    public AgreementId getAgreementId() {
        return agreementId;
    }

    public Agreement buildAgreement(final Crypto crypto,
                                    final long staleAt) throws CryptoException {
        Objects.requireNonNull(recipientsKeyAgreementKey);

        final SessionPair sessionPair = crypto.generateSessionKeyPair(keyPair, recipientsKeyAgreementKey);

        return Agreement.of(agreementId, sessionPair, staleAt);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PendingAgreement that = (PendingAgreement) o;
        return Objects.equals(keyPair, that.keyPair) && Objects.equals(recipientsKeyAgreementKey, that.recipientsKeyAgreementKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyPair, recipientsKeyAgreementKey);
    }

    @Override
    public String toString() {
        return "PendingAgreement{" +
                "keyPair=" + keyPair +
                ", recipientsKeyAgreementKey=" + recipientsKeyAgreementKey +
                '}';
    }
}
