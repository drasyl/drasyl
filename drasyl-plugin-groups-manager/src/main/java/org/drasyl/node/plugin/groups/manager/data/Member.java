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
package org.drasyl.node.plugin.groups.manager.data;

import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.identity.IdentityPublicKey;

import java.util.Objects;

/**
 * Class is used to model the state of a member.
 * <p>
 * <b>This class should only plugin internally used.</b>
 * </p>
 */
public final class Member {
    private final IdentityPublicKey publicKey;

    private Member(final IdentityPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @JsonValue
    public IdentityPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Member that = (Member) o;
        return Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public String toString() {
        return "Member{" +
                "publicKey=" + publicKey +
                '}';
    }

    public static Member of(final IdentityPublicKey publicKey) {
        return new Member(publicKey);
    }
}
