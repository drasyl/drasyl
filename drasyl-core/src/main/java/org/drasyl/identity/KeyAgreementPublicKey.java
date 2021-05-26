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
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;
import com.google.protobuf.ByteString;
import com.goterl.lazysodium.utils.Key;
import org.drasyl.crypto.HexUtil;
import org.drasyl.serialization.JacksonJsonSerializer.BytesToHexStringDeserializer;
import org.drasyl.serialization.JacksonJsonSerializer.BytesToHexStringSerializer;
import org.drasyl.util.InternPool;

import static org.drasyl.crypto.Crypto.PK_CURVE_25519_KEY_LENGTH;

/**
 * This class models a curve25519 public key that is used for x25519 key exchange.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class KeyAgreementPublicKey implements PublicKey {
    public static final short KEY_LENGTH_AS_BYTES = PK_CURVE_25519_KEY_LENGTH;
    @SuppressWarnings("unused")
    public static final short KEY_LENGTH_AS_STRING = KEY_LENGTH_AS_BYTES * 2;
    private static final InternPool<KeyAgreementPublicKey> POOL = new InternPool<>();

    /**
     * See {@link InternPool#intern(Object)}
     */
    public KeyAgreementPublicKey intern() {
        return POOL.intern(this);
    }

    @JsonValue
    @JsonSerialize(using = BytesToHexStringSerializer.class)
    @JsonDeserialize(using = BytesToHexStringDeserializer.class)
    @Override
    public byte[] toByteArray() {
        return getBytes().toByteArray();
    }

    /**
     * @return this key as {@link Key}
     */
    @Override
    public Key toSodiumKey() {
        return Key.fromBytes(toByteArray());
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(toByteArray());
    }

    public static KeyAgreementPublicKey of(final ByteString bytes) {
        if (bytes.size() != KEY_LENGTH_AS_BYTES) {
            throw new IllegalArgumentException("key has wrong size.");
        }
        return new AutoValue_KeyAgreementPublicKey(bytes).intern();
    }

    /**
     * Converts a byte[] into a {@link KeyAgreementPublicKey}.
     *
     * @param bytes public key
     * @return {@link KeyAgreementPublicKey}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @JsonCreator
    public static KeyAgreementPublicKey of(final byte[] bytes) {
        return of(ByteString.copyFrom(bytes));
    }

    /**
     * Converts a {@link String} into a {@link KeyAgreementPublicKey}.
     *
     * @param bytes keyAsHexString as String
     * @return {@link KeyAgreementPublicKey}
     * @throws NullPointerException     if {@code keyAsHexString} is {@code null}
     * @throws IllegalArgumentException if {@code keyAsHexString} does not conform to a valid
     *                                  keyAsHexString string
     */
    @JsonCreator
    public static KeyAgreementPublicKey of(final String bytes) {
        return of(HexUtil.fromString(bytes));
    }
}
