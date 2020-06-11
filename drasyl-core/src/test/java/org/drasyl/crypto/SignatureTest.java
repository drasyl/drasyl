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
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignatureTest {
    private KeyPair keyPair1;
    private KeyPair keyPair2;
    private Signable signable1;
    private Signable signable2;
    private Signature signature1;
    private Signature signature2;

    @BeforeEach
    public void init() {
        signable1 = mock(Signable.class);
        signable2 = mock(Signable.class);
        keyPair1 = Crypto.generateKeys();
        keyPair2 = Crypto.generateKeys();

        when(signable1.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x3a, (byte) 0x22 });
        when(signable2.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x11, (byte) 0x1b });
    }

    @Test
    void testSignableNotNull() {
        assertNotNull(signable1.getSignableBytes());
    }

    @Test
    void testDifferentSignablesHaveDifferentSignatures() throws CryptoException {
        ArgumentCaptor<Signature> signature1ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);
        ArgumentCaptor<Signature> signature2ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);

        Crypto.sign(keyPair1.getPrivate(), signable1);
        verify(signable1).setSignature(signature1ArgumentCaptor.capture());
        signature1 = signature1ArgumentCaptor.getValue();

        Crypto.sign(keyPair1.getPrivate(), signable2);
        verify(signable2).setSignature(signature2ArgumentCaptor.capture());
        signature2 = signature2ArgumentCaptor.getValue();

        assertNotEquals(signature1, signature2);

        // Ignore to String
        signature1.toString();
    }

    @Test
    void testVerifySignature() throws CryptoException {
        ArgumentCaptor<Signature> signature1ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);

        Crypto.sign(keyPair1.getPrivate(), signable1);
        verify(signable1).setSignature(signature1ArgumentCaptor.capture());
        signature1 = signature1ArgumentCaptor.getValue();
        when(signable1.getSignature()).thenReturn(signature1);

        assertNotNull(signature1);
        assertNotNull(signable1);
        boolean verified = Crypto.verifySignature(keyPair1.getPublic(), signable1);
        assertTrue(verified);

        boolean notVerified = Crypto.verifySignature(keyPair2.getPublic(), signable2);
        assertFalse(notVerified);
    }

    @Test
    void testVerifyDifferentSignatures() throws CryptoException {
        ArgumentCaptor<Signature> signature1ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);
        ArgumentCaptor<Signature> signature2ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);

        Crypto.sign(keyPair1.getPrivate(), signable1);
        verify(signable1).setSignature(signature1ArgumentCaptor.capture());
        signature1 = signature1ArgumentCaptor.getValue();
        when(signable1.getSignature()).thenReturn(signature1);

        Crypto.sign(keyPair1.getPrivate(), signable2);
        verify(signable2).setSignature(signature2ArgumentCaptor.capture());
        signature2 = signature2ArgumentCaptor.getValue();
        when(signable2.getSignature()).thenReturn(signature2);

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

    @Test
    void testConflict() throws CryptoException, IOException {
        ByteArrayOutputStream os1 = new ByteArrayOutputStream();
        os1.write(new byte[]{});
        os1.write(new byte[]{
                0x01,
                0x02
        });

        ByteArrayOutputStream os2 = new ByteArrayOutputStream();
        os2.write(new byte[]{
                0x01,
                0x02
        });
        os2.write(new byte[]{});

        when(signable1.getSignableBytes()).thenReturn(os1.toByteArray());
        when(signable2.getSignableBytes()).thenReturn(os2.toByteArray());

        ArgumentCaptor<Signature> signature1ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);
        ArgumentCaptor<Signature> signature2ArgumentCaptor = ArgumentCaptor.forClass(Signature.class);

        Crypto.sign(keyPair1.getPrivate(), signable1);
        verify(signable1).setSignature(signature1ArgumentCaptor.capture());
        signature1 = signature1ArgumentCaptor.getValue();

        Crypto.sign(keyPair1.getPrivate(), signable2);
        verify(signable2).setSignature(signature2ArgumentCaptor.capture());
        signature2 = signature2ArgumentCaptor.getValue();

        assertNotEquals(signature1, signature2);
    }
}