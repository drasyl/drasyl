/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.crypto.CryptoException;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

import static org.drasyl.util.SecretUtil.maskSecret;

public class CompressedKeyPair {
    private final CompressedPublicKey publicKey;
    private final CompressedPrivateKey privateKey;
    private final Identity identity;

    @JsonCreator
    CompressedKeyPair(@JsonProperty("publicKey") CompressedPublicKey publicKey, @JsonProperty("privateKey") CompressedPrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.identity = Identity.of(publicKey);
    }

    public Identity getIdentity() {
        return identity;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public CompressedPrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, privateKey, identity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompressedKeyPair that = (CompressedKeyPair) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(privateKey, that.privateKey) &&
                Objects.equals(identity, that.identity);
    }

    @Override
    public String toString() {
        return "CompressedKeyPair{" +
                "publicKey=" + publicKey +
                ", privateKey=" + maskSecret(privateKey) +
                ", identity=" + identity +
                '}';
    }

    public static CompressedKeyPair of(CompressedPublicKey publicKey,
                                       CompressedPrivateKey privateKey) {
        return new CompressedKeyPair(publicKey, privateKey);
    }

    public static CompressedKeyPair of(String publicKey, String privateKey) throws CryptoException {
        return new CompressedKeyPair(CompressedPublicKey.of(publicKey), CompressedPrivateKey.of(privateKey));
    }

    public static CompressedKeyPair of(KeyPair keyPair) throws CryptoException {
        return of(keyPair.getPublic(), keyPair.getPrivate());
    }

    public static CompressedKeyPair of(PublicKey publicKey,
                                       PrivateKey privateKey) throws CryptoException {
        return new CompressedKeyPair(CompressedPublicKey.of(publicKey), CompressedPrivateKey.of(privateKey));
    }
}
