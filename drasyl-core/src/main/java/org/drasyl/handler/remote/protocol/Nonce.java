/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.remote.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.HexUtil;
import org.drasyl.crypto.sodium.DrasylSodiumWrapper;
import org.drasyl.util.ImmutableByteArray;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.internal.NonNull;
import org.drasyl.util.internal.UnstableApi;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RemoteMessage} is uniquely identified by its {@link #NONCE_LENGTH} bytes long nonce.
 * <p>
 * This is an immutable object.
 */
@UnstableApi
@SuppressWarnings("java:S2974")
public class Nonce {
    public static final boolean PSEUDORANDOM_NONCE = SystemPropertyUtil.getBoolean("org.drasyl.nonce.pseudorandom", false);
    public static final int NONCE_LENGTH = DrasylSodiumWrapper.XCHACHA20POLY1305_IETF_NPUBBYTES;
    private final byte[] bytes;

    private Nonce(@NonNull final byte[] bytes) {
        if (!isValidNonce(bytes)) {
            throw new IllegalArgumentException("NONCE must be a " + NONCE_LENGTH + " bit byte array: " + HexUtil.bytesToHex(bytes));
        }
        this.bytes = requireNonNull(bytes);
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(bytes);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(bytes, NONCE_LENGTH);
    }

    public ImmutableByteArray toImmutableByteArray() {
        return ImmutableByteArray.of(bytes);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Nonce nonce1 = (Nonce) o;
        return Arrays.equals(bytes, nonce1.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    /**
     * Static factory to retrieve a randomly generated {@link Nonce}.
     *
     * @return A randomly generated {@link Nonce}
     */
    public static Nonce randomNonce() {
        final byte[] bytes;
        if (PSEUDORANDOM_NONCE) {
            bytes = RandomUtil.randomBytes(NONCE_LENGTH);
        }
        else {
            bytes = Crypto.randomBytes(NONCE_LENGTH);
        }
        return Nonce.ofDirect(bytes);
    }

    /**
     * Checks if {@code bytes} is a valid value.
     *
     * @param bytes string to be validated
     * @return {@code true} if valid. Otherwise {@code false}
     */
    public static boolean isValidNonce(final byte[] bytes) {
        return bytes != null && bytes.length == NONCE_LENGTH;
    }

    /**
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    static Nonce ofDirect(@NonNull final byte[] bytes) {
        return new Nonce(bytes);
    }

    /**
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public static Nonce of(@NonNull final byte[] bytes) {
        return new Nonce(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public static Nonce of(@NonNull final ImmutableByteArray bytes) {
        return ofDirect(bytes.getArray());
    }

    /**
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public static Nonce of(@NonNull final String bytes) {
        return Nonce.of(HexUtil.parseHexBinary(bytes));
    }

    /**
     * Writes this nonce to the buffer {@code out}.
     *
     * @param byteBuf writes this nonce to the given buffer
     */
    public void writeTo(final ByteBuf out) {
        out.writeBytes(bytes);
    }
}
