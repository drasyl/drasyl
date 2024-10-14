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
package org.drasyl.identity;

import io.netty.buffer.ByteBuf;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.util.ImmutableByteArray;
import org.drasyl.util.InternPool;
import org.drasyl.util.Worm;
import org.drasyl.util.internal.NonNull;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.drasyl.crypto.Crypto.PK_LONG_TIME_KEY_LENGTH;

/**
 * This class models an ed25519 public key that is used as node's unique overlay address.
 * <p>
 * This is an immutable object.
 */
@SuppressWarnings({ "java:S118", "java:S1213" })
public class IdentityPublicKey extends DrasylAddress implements PublicKey {
    public static final short KEY_LENGTH_AS_BYTES = PK_LONG_TIME_KEY_LENGTH;
    public static final short KEY_LENGTH_AS_STRING = KEY_LENGTH_AS_BYTES * 2;
    private static final InternPool<IdentityPublicKey> POOL = new InternPool<>();
    private final transient Worm<KeyAgreementPublicKey> convertedKey = Worm.of();
    public static final IdentityPublicKey ZERO_ID = IdentityPublicKey.of(new byte[KEY_LENGTH_AS_BYTES]);
    private final byte[] bytes;
    private boolean hashCodeSet;
    private int hashCode;

    private IdentityPublicKey(@NonNull final byte[] bytes) {
        this.bytes = requireNonNull(bytes);
    }

    /**
     * @return this public key as key agreement key (curve25519)
     */
    public KeyAgreementPublicKey getLongTimeKeyAgreementKey() {
        return convertedKey.getOrCompute(() -> {
            try {
                return Crypto.INSTANCE.convertIdentityKeyToKeyAgreementKey(this);
            }
            catch (final CryptoException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * See {@link InternPool#intern(Object)}
     */
    public IdentityPublicKey intern() {
        return POOL.intern(this);
    }

    @Override
    public ImmutableByteArray getBytes() {
        return ImmutableByteArray.of(bytes);
    }

    @Override
    public byte[] toByteArray() {
        return getBytes().getArray();
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(getBytes().getArray());
    }

    @Override
    public int hashCode() {
        if (!hashCodeSet) {
            hashCodeSet = true;
            hashCode = getBytes().hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof IdentityPublicKey) {
            final IdentityPublicKey that = (IdentityPublicKey) o;
            return hashCode() == that.hashCode();
        }
        return false;
    }

    @Override
    public void writeTo(final ByteBuf out) {
        out.writeBytes(bytes);
    }

    /**
     * Converts a byte[] into a {@link IdentityPublicKey}.
     *
     * @param bytes public key
     * @return {@link IdentityPublicKey}
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code bytes} has wrong key size
     */
    public static IdentityPublicKey ofDirect(final byte[] bytes) {
        if (bytes.length != KEY_LENGTH_AS_BYTES) {
            throw new IllegalArgumentException("key has wrong size.");
        }
        return new IdentityPublicKey(bytes).intern();
    }

    /**
     * Converts a byte[] into a {@link IdentityPublicKey}.
     *
     * @param bytes public key
     * @return {@link IdentityPublicKey}
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code bytes} has wrong key size
     */
    public static IdentityPublicKey of(final byte[] bytes) {
        if (bytes.length != KEY_LENGTH_AS_BYTES) {
            throw new IllegalArgumentException("key has wrong size.");
        }
        return new IdentityPublicKey(Arrays.copyOf(bytes, KEY_LENGTH_AS_BYTES)).intern();
    }

    /**
     * @throws NullPointerException     if {@code bytes} is {@code null}
     * @throws IllegalArgumentException if {@code bytes} has wrong key size
     */
    public static IdentityPublicKey of(final ImmutableByteArray bytes) {
        return ofDirect(bytes.getArray());
    }

    /**
     * Converts a {@link String} into a {@link IdentityPublicKey}.
     *
     * @param bytes keyAsHexString as String
     * @return {@link IdentityPublicKey}
     * @throws NullPointerException     if {@code keyAsHexString} is {@code null}
     * @throws IllegalArgumentException if {@code keyAsHexString} does not conform to a valid
     *                                  keyAsHexString string
     */
    public static IdentityPublicKey of(final String bytes) {
        return of(HexUtil.fromString(bytes));
    }
}
