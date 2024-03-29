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

import com.google.auto.value.AutoValue;
import org.drasyl.util.internal.NonNull;

/**
 * This class is a simple holder for a key pair (a {@link IdentityPublicKey} and a {@link
 * IdentitySecretKey}). It does not enforce any security, and, when initialized, should be treated
 * like a {@link IdentitySecretKey}.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class KeyPair<P extends PublicKey, S extends SecretKey> {
    @NonNull
    public abstract P getPublicKey();

    @NonNull
    public abstract S getSecretKey();

    /**
     * Unlike {@link #toString()}, this method returns the key pair with the unmasked secret key.
     *
     * @return key pair with unmasked secret key
     */
    public String toUnmaskedString() {
        return "KeyPair{" +
                "publicKey=" + getPublicKey() + ", " +
                "secretKey=" + getSecretKey().toUnmaskedString() +
                "}";
    }

    /**
     * @throws NullPointerException if {@code publiceKey} or {@code secretKey} is {@code null}.
     */
    public static <P extends PublicKey, S extends SecretKey> KeyPair<P, S> of(final P publicKey,
                                                                              final S secretKey) {
        return new AutoValue_KeyPair<>(publicKey, secretKey);
    }
}
