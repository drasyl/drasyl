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
import com.google.protobuf.ByteString;
import org.drasyl.util.InternPool;

import static org.drasyl.crypto.Crypto.PK_CURVE_25519_KEY_LENGTH;

/**
 * This class models a public key that can be converted into a string and vice versa.
 * <p>
 * This is an immutable object.
 */
public class KeyAgreementPublicKey extends PublicKey {
    public static final short KEY_LENGTH_AS_BYTES = PK_CURVE_25519_KEY_LENGTH;
    public static final short KEY_LENGTH_AS_STRING = KEY_LENGTH_AS_BYTES * 2;
    private static final InternPool<KeyAgreementPublicKey> POOL = new InternPool<>();

    /**
     * Creates a new public keyAsHexString from the given string.
     *
     * @param keyAsHexString public keyAsHexString
     * @throws NullPointerException     if {@code keyAsHexString} is {@code null}
     * @throws IllegalArgumentException if {@code keyAsHexString} does not conform to a valid
     *                                  string
     */
    private KeyAgreementPublicKey(final String keyAsHexString) {
        super(keyAsHexString);
    }

    /**
     * Creates a new public key from the given byte array.
     *
     * @param key public key
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is empty
     */
    private KeyAgreementPublicKey(final ByteString key) {
        super(key);
    }

    /**
     * Converts a {@link String} into a {@link KeyAgreementPublicKey}.
     *
     * @param keyAsHexString keyAsHexString as String
     * @return {@link KeyAgreementPublicKey}
     * @throws NullPointerException     if {@code keyAsHexString} is {@code null}
     * @throws IllegalArgumentException if {@code keyAsHexString} does not conform to a valid
     *                                  keyAsHexString string
     */
    public static KeyAgreementPublicKey of(final String keyAsHexString) {
        return new KeyAgreementPublicKey(keyAsHexString).intern();
    }

    /**
     * Converts a byte[] into a {@link KeyAgreementPublicKey}.
     *
     * @param key public key
     * @return {@link KeyAgreementPublicKey}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @JsonCreator
    public static KeyAgreementPublicKey of(final byte[] key) {
        return of(ByteString.copyFrom(key));
    }

    public static KeyAgreementPublicKey of(final ByteString key) {
        return new KeyAgreementPublicKey(key).intern();
    }

    @Override
    public boolean validLength() {
        return this.key.size() == KEY_LENGTH_AS_BYTES;
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public KeyAgreementPublicKey intern() {
        return POOL.intern(this);
    }
}
