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
import com.goterl.lazysodium.utils.Key;
import org.drasyl.crypto.HexUtil;
import org.drasyl.serialization.JacksonJsonSerializer;

import java.util.Arrays;

public abstract class AbstractKey {
    @JsonValue
    @JsonSerialize(using = JacksonJsonSerializer.BytesToHexStringSerializer.class)
    @JsonDeserialize(using = JacksonJsonSerializer.BytesToHexStringDeserializer.class)
    protected final byte[] key;

    AbstractKey() {
        key = null;
    }

    /**
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} has wrong length
     */
    protected AbstractKey(final byte[] key) {
        this.key = key;
        if (!validLength()) {
            throw new IllegalArgumentException("key has wrong size.");
        }
    }

    /**
     * @throws NullPointerException     if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} does not conform to a valid hexadecimal
     *                                  scheme or has wrong length
     */
    @JsonCreator
    protected AbstractKey(final String key) {
        this(HexUtil.fromString(key));
    }

    /**
     * @return {@code true} if key has a valid length
     */
    public abstract boolean validLength();

    public byte[] getKey() {
        return this.key;
    }

    /**
     * @return this key as {@link Key}
     */
    public Key toSodiumKey() {
        return Key.fromBytes(key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractKey that = (AbstractKey) o;
        return Arrays.equals(key, that.key);
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(this.key);
    }
}
