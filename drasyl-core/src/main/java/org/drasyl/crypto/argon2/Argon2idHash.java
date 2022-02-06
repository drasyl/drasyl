/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.crypto.argon2;

import org.drasyl.util.ImmutableByteArray;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.UnsignedShort;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Argon2idHash {
    public static final short VERSION = 0x13; //v
    public static final String SALT = "AAAAAAAAAAAAAAAAAAAAAA";
    public static final short LANES = 1; //p
    private static final Pattern PATTERN = Pattern.compile("\\$argon2id\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$(.+)\\$(.+)");
    private final UnsignedInteger memoryCost; //m
    private final UnsignedShort timeCost; //t
    private final ImmutableByteArray hash;

    private Argon2idHash(final UnsignedInteger memoryCost,
                         final UnsignedShort timeCost,
                         final ImmutableByteArray hash) {
        this.memoryCost = memoryCost;
        this.timeCost = timeCost;
        this.hash = hash;
    }

    public static Argon2idHash of(final long memoryCost,
                                  final int timeCost,
                                  final byte[] hash) {
        return new Argon2idHash(
                UnsignedInteger.of(memoryCost / 1024),
                UnsignedShort.of(timeCost),
                ImmutableByteArray.of(Base64.getEncoder().encode(hash)));
    }

    public static Argon2idHash of(final byte[] encodedHash) {
        final Matcher matcher = PATTERN.matcher(new String(encodedHash));
        if (matcher.find()) {
            final UnsignedInteger m = UnsignedInteger.of(Long.parseLong(matcher.group(2)));
            final UnsignedShort t = UnsignedShort.of(Integer.parseInt(matcher.group(3)));
            final ImmutableByteArray hash = ImmutableByteArray.of(matcher.group(6).getBytes(StandardCharsets.UTF_8));

            return new Argon2idHash(m, t, hash);
        }
        else {
            throw new IllegalArgumentException("Not a valid argon2id hash");
        }
    }

    public UnsignedInteger getMemoryCost() {
        return memoryCost;
    }

    public UnsignedShort getTimeCost() {
        return timeCost;
    }

    public ImmutableByteArray getHash() {
        return ImmutableByteArray.of(Base64.getDecoder().decode(hash.getArray()));
    }

    public byte[] getEncodedHash() {
        return String.format("$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
                VERSION,
                memoryCost.getValue(),
                timeCost.getValue(),
                LANES,
                SALT,
                new String(hash.getArray()).replace("=", "")).getBytes(StandardCharsets.UTF_8);
    }
}
