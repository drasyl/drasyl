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
import org.drasyl.util.InternPool;

import static org.drasyl.crypto.Crypto.SK_CURVE_25519_KEY_LENGTH;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class models a secret key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class KeyAgreementSecretKey extends SecretKey {
    public static final short KEY_LENGTH_AS_BYTES = SK_CURVE_25519_KEY_LENGTH;
    public static final short KEY_LENGTH_AS_STRING = KEY_LENGTH_AS_BYTES * 2;
    private static final InternPool<KeyAgreementSecretKey> POOL = new InternPool<>();

    /**
     * Creates a new secret key from the given string.
     *
     * @param keyAsHexString secret key
     * @throws IllegalArgumentException if the string parameter does not conform to a valid key
     */
    private KeyAgreementSecretKey(final String keyAsHexString) {
        super(keyAsHexString);
    }

    /**
     * Creates a new secret key from the given byte array.
     *
     * @param key secret key
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is empty
     */
    private KeyAgreementSecretKey(final byte[] key) {
        super(key);
    }

    /**
     * Converts a {@link String} into a {@link KeyAgreementSecretKey}.
     *
     * @param keyAsHexString keyAsHexString as String
     * @return {@link KeyAgreementSecretKey}
     * @throws IllegalArgumentException if string parameter does not conform to a valid
     *                                  keyAsHexString
     */
    public static KeyAgreementSecretKey of(final String keyAsHexString) {
        return new KeyAgreementSecretKey(keyAsHexString).intern();
    }

    /**
     * Converts a byte[] into a {@link KeyAgreementSecretKey}.
     *
     * @param key key as byte array
     * @return {@link IdentityPublicKey}
     */
    @JsonCreator
    public static KeyAgreementSecretKey of(final byte[] key) {
        return new KeyAgreementSecretKey(key).intern();
    }

    @Override
    public boolean validLength() {
        return this.key.length == KEY_LENGTH_AS_BYTES;
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public KeyAgreementSecretKey intern() {
        return POOL.intern(this);
    }

    @Override
    public String toString() {
        return maskSecret(super.toString());
    }

    public String toUnmaskedString() {
        return super.toString();
    }
}
