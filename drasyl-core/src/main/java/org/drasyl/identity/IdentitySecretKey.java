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

import static org.drasyl.crypto.Crypto.SK_LONG_TIME_KEY_LENGTH;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class models a ed25519 private key.
 * <p>
 * This is an immutable object.
 */
@AutoValue
@SuppressWarnings("java:S118")
public abstract class IdentitySecretKey implements SecretKey {
    public static final short KEY_LENGTH_AS_BYTES = SK_LONG_TIME_KEY_LENGTH;
    @SuppressWarnings("unused")
    public static final short KEY_LENGTH_AS_STRING = KEY_LENGTH_AS_BYTES * 2;
    private static final InternPool<IdentitySecretKey> POOL = new InternPool<>();

    /**
     * See {@link InternPool#intern(Object)}
     */
    public IdentitySecretKey intern() {
        return POOL.intern(this);
    }

    @Override
    public String toString() {
        return maskSecret(HexUtil.bytesToHex(toByteArray()));
    }

    @Override
    public String toUnmaskedString() {
        return HexUtil.bytesToHex(toByteArray());
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

    public static IdentitySecretKey of(final ByteString bytes) {
        if (bytes.size() != KEY_LENGTH_AS_BYTES) {
            throw new IllegalArgumentException("key has wrong size.");
        }
        return new AutoValue_IdentitySecretKey(bytes).intern();
    }

    /**
     * Converts a byte[] into a {@link IdentitySecretKey}.
     *
     * @param bytes key as byte array
     * @return {@link IdentityPublicKey}
     */
    @JsonCreator
    public static IdentitySecretKey of(final byte[] bytes) {
        return of(ByteString.copyFrom(bytes));
    }

    /**
     * Converts a {@link String} into a {@link IdentitySecretKey}.
     *
     * @param bytes keyAsHexString as String
     * @return {@link IdentitySecretKey}
     * @throws IllegalArgumentException if string parameter does not conform to a valid
     *                                  keyAsHexString
     */
    @JsonCreator
    public static IdentitySecretKey of(final String bytes) {
        return of(HexUtil.fromString(bytes));
    }
}
