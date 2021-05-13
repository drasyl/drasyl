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
package org.drasyl.remote.protocol;

import com.goterl.lazysodium.interfaces.AEAD;
import org.drasyl.annotation.NonNull;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.HexUtil;
import org.drasyl.pipeline.message.AddressedEnvelope;

import java.util.Arrays;

/**
 * A {@link AddressedEnvelope} is uniquely identified by its {@link #NONCE_LENGTH} bytes long
 * nonce.
 * <p>
 * This is an immutable object.
 */
public class Nonce {
    public static final int NONCE_LENGTH = AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES;
    private final byte[] nonce; //NOSONAR

    private Nonce(@NonNull final byte[] nonce) {
        if (!isValidNonce(nonce)) {
            throw new IllegalArgumentException("NONCE must be a " + NONCE_LENGTH + " bit byte array: " + HexUtil.bytesToHex(nonce));
        }
        this.nonce = nonce.clone();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nonce);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Nonce o2 = (Nonce) o;
        return Arrays.equals(this.nonce, o2.nonce);
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(nonce);
    }

    public byte[] byteArrayValue() {
        return nonce.clone();
    }

    /**
     * Static factory to retrieve a randomly generated {@link Nonce}.
     *
     * @return A randomly generated {@link Nonce}
     */
    public static Nonce randomNonce() {
        return new Nonce(Crypto.randomBytes(NONCE_LENGTH));
    }

    /**
     * Checks if {@code nonce} is a valid value.
     *
     * @param nonce string to be validated
     * @return {@code true} if valid. Otherwise {@code false}
     */
    public static boolean isValidNonce(final byte[] nonce) {
        return nonce != null && nonce.length == NONCE_LENGTH;
    }

    /**
     * @throws NullPointerException if {@code nonce} is {@code null}
     */
    public static Nonce of(@NonNull final byte[] nonce) {
        return new Nonce(nonce);
    }

    /**
     * @throws NullPointerException if {@code nonce} is {@code null}
     */
    public static Nonce of(@NonNull final String nonce) {
        return new Nonce(HexUtil.parseHexBinary(nonce));
    }
}
