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
package org.drasyl.node.handler.crypto;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.Hashing;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.util.ImmutableByteArray;

import java.util.Objects;

/**
 * This class represents an identifier for an {@link Agreement} between to nodes.
 */
@SuppressWarnings("java:S2974")
public class AgreementId {
    public static final short ID_LENGTH = 4;
    private final ImmutableByteArray id;

    /**
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is not a valid MurMur3x32 hash
     */
    private AgreementId(final ImmutableByteArray id) {
        if (id.size() != ID_LENGTH) {
            throw new IllegalArgumentException("id is not a valid MurMur3x32 hash.");
        }

        this.id = id;
    }

    /**
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is not a valid MurMur3x32 hash
     */
    public static AgreementId of(final byte[] id) {
        return of(ImmutableByteArray.of(id));
    }

    public static AgreementId of(final ImmutableByteArray id) {
        return new AgreementId(id);
    }

    /**
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if {@code id} is not a valid MurMur3x32 hash
     */
    public static AgreementId of(final KeyAgreementPublicKey pk1, final KeyAgreementPublicKey pk2) {
        final int compare = Crypto.compare(pk1, pk2);

        switch (compare) {
            case -1:
                return of(Hashing.murmur3x32(pk1.toByteArray(), pk2.toByteArray()));
            case 1:
                return of(Hashing.murmur3x32(pk2.toByteArray(), pk1.toByteArray()));
            case 0:
            default:
                throw new IllegalArgumentException("pk1 and pk2 must be not equals.");
        }
    }

    public ImmutableByteArray getId() {
        return id;
    }

    public byte[] toBytes() {
        return id.getArray();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AgreementId agreementId = (AgreementId) o;
        return Objects.equals(id, agreementId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AgreementId{" +
                "id='" + id + '\'' +
                '}';
    }
}
