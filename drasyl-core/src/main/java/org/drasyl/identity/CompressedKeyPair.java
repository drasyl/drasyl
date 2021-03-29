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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.KeyPair;
import java.util.Objects;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class is a simple holder for a compressed key pair (a {@link CompressedPublicKey} and a
 * {@link CompressedPrivateKey}). It does not enforce any security, and, when initialized, should be
 * treated like a {@link CompressedPrivateKey}.
 * <p>
 * This is an immutable object.
 */
public class CompressedKeyPair {
    private final CompressedPublicKey publicKey;
    private final CompressedPrivateKey privateKey;

    @JsonCreator
    CompressedKeyPair(@JsonProperty("publicKey") final CompressedPublicKey publicKey,
                      @JsonProperty("privateKey") final CompressedPrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public CompressedPrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * @throws IllegalStateException if uncompressed public or private keys could not be generated
     */
    public KeyPair toUncompressedKeyPair() {
        return new KeyPair(publicKey.toUncompressedKey(), privateKey.toUncompressedKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, privateKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CompressedKeyPair that = (CompressedKeyPair) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(privateKey, that.privateKey);
    }

    @Override
    public String toString() {
        return "CompressedKeyPair{" +
                "publicKey=" + publicKey +
                ", privateKey=" + maskSecret(privateKey) +
                '}';
    }

    public static CompressedKeyPair of(final CompressedPublicKey publicKey,
                                       final CompressedPrivateKey privateKey) {
        return new CompressedKeyPair(publicKey, privateKey);
    }

    public static CompressedKeyPair of(final String publicKey,
                                       final String privateKey) {
        return new CompressedKeyPair(CompressedPublicKey.of(publicKey), CompressedPrivateKey.of(privateKey));
    }

    public static CompressedKeyPair of(final byte[] publicKey,
                                       final byte[] privateKey) {
        return new CompressedKeyPair(CompressedPublicKey.of(publicKey), CompressedPrivateKey.of(privateKey));
    }
}
