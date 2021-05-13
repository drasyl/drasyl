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
package org.drasyl.identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class is a simple holder for a key pair (a {@link IdentityPublicKey} and a {@link
 * IdentitySecretKey}). It does not enforce any security, and, when initialized, should be treated
 * like a {@link IdentitySecretKey}.
 * <p>
 * This is an immutable object.
 */
public class KeyPair<P extends PublicKey, S extends SecretKey> {
    private final P publicKey;
    private final S secretKey;

    @JsonCreator
    KeyPair(@JsonProperty("publicKey") final P publicKey,
            @JsonProperty("secretKey") final S secretKey) {
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    public static <P extends PublicKey, S extends SecretKey> KeyPair<P, S> of(final P publicKey,
                                                                              final S secretKey) {
        return new KeyPair<>(publicKey, secretKey);
    }

    public P getPublicKey() {
        return publicKey;
    }

    public S getSecretKey() {
        return secretKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, secretKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyPair<?, ?> that = (KeyPair<?, ?>) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(secretKey, that.secretKey);
    }

    @Override
    public String toString() {
        return "KeyPair{" +
                "publicKey=" + publicKey +
                ", secretKey=" + maskSecret(secretKey) +
                '}';
    }
}
