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

import org.drasyl.identity.CompressedKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureTest {
    private KeyPair keyPair1;
    private KeyPair keyPair2;
    @Mock
    private Signable signable1;
    @Mock
    private Signable signable2;
    private Signature signature1;
    private Signature signature2;

    @BeforeEach
    void setUp() throws CryptoException {
        keyPair1 = CompressedKeyPair.of("0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c", "00b96ac2757f5f427a210c7a68f357bfa03f986b547a3b68e0bf79daa45f9edd").toUncompressedKeyPair();
        keyPair2 = CompressedKeyPair.of("0223784f9068273f004d61fa8ae92639a6b7c92cd7c5cb8ed45a36ca492f98d603", "07d7ed69896296e3622504317881b25ff77de4f5b7f1cad15ac73ee1e8ab7115").toUncompressedKeyPair();
    }

    @Nested
    class GetSignableBytes {
        @Test
        void shouldNotReturnNull() {
            when(signable1.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x3a, (byte) 0x22 });

            assertNotNull(signable1.getSignableBytes());
        }
    }

    @Test
    void testDifferentSignablesHaveDifferentSignatures() throws CryptoException {
        when(signable1.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x3a, (byte) 0x22 });
        when(signable2.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x11, (byte) 0x1b });

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
        when(signable1.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x3a, (byte) 0x22 });

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
        when(signable1.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x3a, (byte) 0x22 });
        when(signable2.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x11, (byte) 0x1b });

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

    @Nested
    class Equals {
        @Test
        void differentKeychainsHaveDifferentKeys() {
            assertNotEquals(keyPair1.getPublic(), keyPair2.getPublic());
            assertNotEquals(keyPair1.getPrivate(), keyPair2.getPrivate());
        }
    }

    @Test
    void testConflict() throws CryptoException, IOException {
        when(signable1.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x3a, (byte) 0x22 });
        when(signable2.getSignableBytes()).thenReturn(new byte[]{ (byte) 0x11, (byte) 0x1b });

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