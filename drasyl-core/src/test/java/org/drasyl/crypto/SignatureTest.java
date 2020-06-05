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
package org.drasyl.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureTest {
    private KeyPair keyPair1;
    private KeyPair keyPair2;
    private DummySignable signable1;
    private DummySignable signable2;
    private Signature signature1;
    private Signature signature2;

    @BeforeEach
    public void init() {
        signable1 = new DummySignable(new byte[]{ (byte) 0x3a, (byte) 0x22 });
        signable2 = new DummySignable(new byte[]{ (byte) 0x11, (byte) 0x1b });
        keyPair1 = Crypto.generateKeys();
        keyPair2 = Crypto.generateKeys();
    }

    @Test
    void testSignableNotNull() {
        signable1 = new DummySignable();
        assertNotNull(signable1.getSignableBytes());
    }

    @Test
    void testDifferentSignablesHaveDifferentSignatures() throws CryptoException {
        Crypto.sign(keyPair1.getPrivate(), signable1);
        signature1 = signable1.getSignature();
        Crypto.sign(keyPair1.getPrivate(), signable2);
        signature2 = signable2.getSignature();
        assertNotEquals(signature1, signature2);
    }

    @Test
    void testVerifySignature() throws CryptoException {
        Crypto.sign(keyPair1.getPrivate(), signable1);
        signature1 = signable1.getSignature();
        assertNotNull(signature1);
        assertNotNull(signable1);
        boolean verified = Crypto.verifySignature(keyPair1.getPublic(), signable1);
        assertTrue(verified);

        boolean notVerified = Crypto.verifySignature(keyPair2.getPublic(), signable2);
        assertFalse(notVerified);
    }

    @Test
    void testVerifyDifferentSignatures() throws CryptoException {
        Crypto.sign(keyPair1.getPrivate(), signable1);
        signature1 = signable1.getSignature();
        Crypto.sign(keyPair1.getPrivate(), signable2);
        signature2 = signable2.getSignature();
        assertNotEquals(signature1, signature2);

        boolean verified1 = Crypto.verifySignature(keyPair1.getPublic(), signable1);
        assertTrue(verified1);
        boolean verified2 = Crypto.verifySignature(keyPair1.getPublic(), signable2);
        assertTrue(verified2);

        boolean notVerified1 = Crypto.verifySignature(keyPair2.getPublic(), signable2);
        assertFalse(notVerified1);
        boolean notVerified2 = Crypto.verifySignature(keyPair2.getPublic(), signable1);
        assertFalse(notVerified2);
    }

    @Test
    void testDifferentKeychainsHaveDifferentKeys() {
        assertNotEquals(keyPair1.getPublic(), keyPair2.getPublic());
        assertNotEquals(keyPair1.getPrivate(), keyPair2.getPrivate());
    }
}