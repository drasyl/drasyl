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
package org.drasyl.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * Represents a confidential string (like a password or a secret token) whose content is masked in
 * {@link #toString()}. Do not use this class if the length of the string must not be revealed.
 */
public final class MaskedString {
    @JsonValue
    private final String string;

    @JsonCreator
    private MaskedString(final String string) {
        this.string = string;
    }

    /**
     * Returns a masked representation of this {@link String}. Each character is replaced with
     * {@code *}.
     *
     * @return masked representation of this {@link String}. Each character is replaced with {@code
     * *}.
     */
    @Override
    public String toString() {
        return maskSecret(string);
    }

    /**
     * Returns the unmasked representation of this {@link String}.
     *
     * @return unmasked representation of this {@link String}
     */
    public String toUnmaskedString() {
        return string;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MaskedString that = (MaskedString) o;
        return Objects.equals(string, that.string);
    }

    @Override
    public int hashCode() {
        return Objects.hash(string);
    }

    public static MaskedString of(final String string) {
        return new MaskedString(string);
    }
}
