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
package org.drasyl.crypto;

import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.interfaces.AEAD;
import com.goterl.lazysodium.utils.SessionPair;
import org.drasyl.crypto.loader.DrasylLazySodiumJava;
import org.drasyl.crypto.loader.DrasylSodiumJava;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyPair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoTest {
    @Nested
    class LongTimeKeyPair {
        @Test
        void shouldGenerate(@Mock final DrasylLazySodiumJava sodium) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            doReturn(true).when(sodium).cryptoSignKeypair(any(), any());

            final KeyPair keyPair = crypto.generateLongTimeKeyPair();

            assertNotNull(keyPair.getPublicKey());
            assertNotNull(keyPair.getSecretKey());
            verify(sodium).cryptoSignKeypair(any(), any());
        }

        @Test
        void shouldThrowExceptionOnError(@Mock final DrasylLazySodiumJava sodium) {
            final Crypto crypto = new Crypto(sodium);
            doReturn(false).when(sodium).cryptoSignKeypair(any(), any());

            assertThrows(CryptoException.class, crypto::generateLongTimeKeyPair);
        }

        @Test
        void shouldConvertKey(@Mock final DrasylLazySodiumJava sodium,
                              @Mock final KeyPair keyPair,
                              @Mock final IdentityPublicKey pk,
                              @Mock final IdentitySecretKey sk) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            when(keyPair.getPublicKey()).thenReturn(pk);
            when(keyPair.getSecretKey()).thenReturn(sk);
            doReturn(true).when(sodium).convertPublicKeyEd25519ToCurve25519(any(), any());
            doReturn(true).when(sodium).convertSecretKeyEd25519ToCurve25519(any(), any());

            final KeyPair rtnKeyPair = crypto.convertLongTimeKeyPairToKeyAgreementKeyPair(keyPair);

            assertNotEquals(pk, rtnKeyPair.getPublicKey());
            assertNotEquals(sk, rtnKeyPair.getSecretKey());
            assertNotNull(rtnKeyPair.getPublicKey());
            assertNotNull(rtnKeyPair.getSecretKey());
            verify(sodium).convertPublicKeyEd25519ToCurve25519(any(), any());
            verify(sodium).convertSecretKeyEd25519ToCurve25519(any(), any());
        }

        @Test
        void shouldThrowExceptionOnWrongKeyToConvert(@Mock final DrasylLazySodiumJava sodium,
                                                     @Mock final KeyPair keyPair,
                                                     @Mock final IdentityPublicKey pk,
                                                     @Mock final IdentitySecretKey sk) {
            final Crypto crypto = new Crypto(sodium);
            when(keyPair.getPublicKey()).thenReturn(pk);
            when(keyPair.getSecretKey()).thenReturn(sk);
            doReturn(true).when(sodium).convertPublicKeyEd25519ToCurve25519(any(), any());
            doReturn(false).when(sodium).convertSecretKeyEd25519ToCurve25519(any(), any());

            assertThrows(CryptoException.class, () -> crypto.convertLongTimeKeyPairToKeyAgreementKeyPair(keyPair));
        }

        @Test
        void shouldConvertPublicKey(@Mock final DrasylLazySodiumJava sodium,
                                    @Mock final IdentityPublicKey pk) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            doReturn(true).when(sodium).convertPublicKeyEd25519ToCurve25519(any(), any());

            final KeyAgreementPublicKey rtnPk = crypto.convertIdentityKeyToKeyAgreementKey(pk);

            assertNotNull(rtnPk);
            verify(sodium).convertPublicKeyEd25519ToCurve25519(any(), any());
        }

        @Test
        void shouldThrowExceptionOnWrongPublicKeyToConvert(@Mock final DrasylLazySodiumJava sodium,
                                                           @Mock final IdentityPublicKey pk) {
            final Crypto crypto = new Crypto(sodium);
            doReturn(false).when(sodium).convertPublicKeyEd25519ToCurve25519(any(), any());

            assertThrows(CryptoException.class, () -> crypto.convertIdentityKeyToKeyAgreementKey(pk));
        }
    }

    @Nested
    class EphemeralKeyPair {
        @Test
        void shouldGenerate(@Mock final DrasylLazySodiumJava sodium,
                            @Mock final UnitSodium sodiumJava) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            doReturn(sodiumJava).when(sodium).getSodium();
            doReturn(true).when(sodium).successful(anyInt());

            final KeyPair keyPair = crypto.generateEphemeralKeyPair();

            assertNotNull(keyPair.getPublicKey());
            assertNotNull(keyPair.getSecretKey());
            verify(sodiumJava).crypto_kx_keypair(any(), any());
            verify(sodium).successful(anyInt());
        }

        @Test
        void shouldThrowExceptionOnError(@Mock final DrasylLazySodiumJava sodium,
                                         @Mock final UnitSodium sodiumJava) {
            final Crypto crypto = new Crypto(sodium);
            doReturn(sodiumJava).when(sodium).getSodium();
            doReturn(false).when(sodium).successful(anyInt());
            doReturn(0).when(sodiumJava).crypto_kx_keypair(any(), any());

            assertThrows(CryptoException.class, crypto::generateEphemeralKeyPair);
            verify(sodiumJava).crypto_kx_keypair(any(), any());
            verify(sodium).successful(anyInt());
        }
    }

    @Nested
    class SessionPairTest {
        @Test
        void shouldGenerateOnSmallerOwnKey(@Mock final DrasylLazySodiumJava sodium,
                                           @Mock final KeyPair keyPair,
                                           @Mock final IdentityPublicKey pk,
                                           @Mock final IdentitySecretKey sk,
                                           @Mock final IdentityPublicKey key,
                                           @Mock final SessionPair sp) throws CryptoException, SodiumException {
            final Crypto crypto = new Crypto(sodium);
            when(keyPair.getPublicKey()).thenReturn(pk);
            when(keyPair.getSecretKey()).thenReturn(sk);
            when(pk.toByteArray()).thenReturn(new byte[]{ 0x01 });
            when(key.toByteArray()).thenReturn(new byte[]{ 0x02 });
            doReturn(sp).when(sodium).cryptoKxClientSessionKeys(any(), any(), any());

            final SessionPair rtnKeyPair = crypto.generateSessionKeyPair(keyPair, key);

            assertEquals(sp, rtnKeyPair);
            verify(sodium).cryptoKxClientSessionKeys(pk.toSodiumKey(), sk.toSodiumKey(), key.toSodiumKey());
            verify(sodium, never()).cryptoKxServerSessionKeys(pk.toSodiumKey(), sk.toSodiumKey(), key.toSodiumKey());
        }

        @Test
        void shouldGenerateOnBiggerOwnKey(@Mock final DrasylLazySodiumJava sodium,
                                          @Mock final KeyPair keyPair,
                                          @Mock final IdentityPublicKey pk,
                                          @Mock final IdentitySecretKey sk,
                                          @Mock final IdentityPublicKey key,
                                          @Mock final SessionPair sp) throws CryptoException, SodiumException {
            final Crypto crypto = new Crypto(sodium);
            when(keyPair.getPublicKey()).thenReturn(pk);
            when(keyPair.getSecretKey()).thenReturn(sk);
            when(pk.toByteArray()).thenReturn(new byte[]{ 0x02 });
            when(key.toByteArray()).thenReturn(new byte[]{ 0x01 });
            doReturn(sp).when(sodium).cryptoKxServerSessionKeys(any(), any(), any());

            final SessionPair rtnKeyPair = crypto.generateSessionKeyPair(keyPair, key);

            assertEquals(sp, rtnKeyPair);
            verify(sodium).cryptoKxServerSessionKeys(pk.toSodiumKey(), sk.toSodiumKey(), key.toSodiumKey());
            verify(sodium, never()).cryptoKxClientSessionKeys(pk.toSodiumKey(), sk.toSodiumKey(), key.toSodiumKey());
        }

        @Test
        void shouldThrowExceptionOnEqualsKeys(@Mock final DrasylLazySodiumJava sodium,
                                              @Mock final KeyPair keyPair,
                                              @Mock final IdentityPublicKey pk,
                                              @Mock final IdentityPublicKey key) {
            final Crypto crypto = new Crypto(sodium);
            when(keyPair.getPublicKey()).thenReturn(pk);
            when(pk.toByteArray()).thenReturn(new byte[]{ 0x01 });
            when(key.toByteArray()).thenReturn(new byte[]{ 0x01 });

            assertThrows(CryptoException.class, () -> crypto.generateSessionKeyPair(keyPair, key));

            verifyNoInteractions(sodium);
        }

        @Test
        void shouldRethrowException(@Mock final DrasylLazySodiumJava sodium,
                                    @Mock final KeyPair keyPair,
                                    @Mock final IdentityPublicKey pk,
                                    @Mock final IdentitySecretKey sk,
                                    @Mock final IdentityPublicKey key) throws SodiumException {
            final Crypto crypto = new Crypto(sodium);
            when(keyPair.getPublicKey()).thenReturn(pk);
            when(keyPair.getSecretKey()).thenReturn(sk);
            when(pk.toByteArray()).thenReturn(new byte[]{ 0x01 });
            when(key.toByteArray()).thenReturn(new byte[]{ 0x02 });
            when(sodium.cryptoKxClientSessionKeys(any(), any(), any())).thenThrow(SodiumException.class);

            assertThrows(CryptoException.class, () -> crypto.generateSessionKeyPair(keyPair, key));
        }
    }

    @Nested
    class Encrypt {
        @Test
        void shouldEncrypt(@Mock final DrasylLazySodiumJava sodium,
                           @Mock final Nonce nonce,
                           @Mock final com.goterl.lazysodium.utils.SessionPair sessionPair) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            when(sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
                    any(), any(), any(), anyLong(), any(), anyLong(), any(), any(), any())).thenReturn(true);
            final byte[] message = new byte[0];

            crypto.encrypt(message, new byte[0], nonce, sessionPair);

            verify(sodium).cryptoAeadXChaCha20Poly1305IetfEncrypt(
                    new byte[AEAD.XCHACHA20POLY1305_IETF_ABYTES], null, message,
                    message.length, new byte[0], 0,
                    null, nonce.toByteArray(), sessionPair.getTx());
        }

        @Test
        void shouldThrowExceptionOnError(@Mock final DrasylLazySodiumJava sodium,
                                         @Mock final Nonce nonce,
                                         @Mock final com.goterl.lazysodium.utils.SessionPair sessionPair) {
            final Crypto crypto = new Crypto(sodium);
            when(sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
                    any(), any(), any(), anyLong(), any(), anyLong(), any(), any(), any())).thenReturn(false);
            final byte[] message = new byte[0];

            assertThrows(CryptoException.class, () -> crypto.encrypt(message, new byte[0], nonce, sessionPair));
        }
    }

    @Nested
    class Decrypt {
        @Test
        void shouldDecrypt(@Mock final DrasylLazySodiumJava sodium,
                           @Mock final Nonce nonce,
                           @Mock final com.goterl.lazysodium.utils.SessionPair sessionPair) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            when(sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                    any(), any(), any(), any(), anyLong(), any(), anyLong(), any(), any())).thenReturn(true);
            final byte[] cipher = new byte[16];

            crypto.decrypt(cipher, new byte[0], nonce, sessionPair);

            verify(sodium).cryptoAeadXChaCha20Poly1305IetfDecrypt(
                    new byte[0], null, null, cipher,
                    cipher.length, new byte[0], 0,
                    nonce.toByteArray(), sessionPair.getTx());
        }

        @Test
        void shouldThrowExceptionOnError(@Mock final DrasylLazySodiumJava sodium,
                                         @Mock final Nonce nonce,
                                         @Mock final com.goterl.lazysodium.utils.SessionPair sessionPair) {
            final Crypto crypto = new Crypto(sodium);
            when(sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                    any(), any(), any(), any(), anyLong(), any(), anyLong(), any(), any())).thenReturn(false);
            final byte[] cipher = new byte[16];

            assertThrows(CryptoException.class, () -> crypto.decrypt(cipher, new byte[0], nonce, sessionPair));
        }
    }

    @Nested
    class Sign {
        @Test
        void shouldSign(@Mock final DrasylLazySodiumJava sodium,
                        @Mock final IdentitySecretKey key) throws CryptoException {
            final Crypto crypto = new Crypto(sodium);
            final byte[] message = new byte[0];
            when(sodium.cryptoSignDetached(any(), any(), anyLong(), any())).thenReturn(true);

            crypto.sign(message, key);

            verify(sodium).cryptoSignDetached(
                    new byte[com.goterl.lazysodium.interfaces.Sign.BYTES], message, message.length,
                    key.toByteArray());
        }

        @Test
        void shouldThrowExceptionOnError(@Mock final DrasylLazySodiumJava sodium,
                                         @Mock final IdentitySecretKey key) {
            final Crypto crypto = new Crypto(sodium);
            final byte[] message = new byte[0];
            when(sodium.cryptoSignDetached(any(), any(), anyLong(), any())).thenReturn(false);

            assertThrows(CryptoException.class, () -> crypto.sign(message, key));
        }

        @Test
        void shouldVerifySignature(@Mock final DrasylLazySodiumJava sodium,
                                   @Mock final IdentityPublicKey key) {
            final Crypto crypto = new Crypto(sodium);
            final byte[] message = new byte[0];
            final byte[] signature = new byte[0];

            crypto.verifySignature(signature, message, key);

            verify(sodium).cryptoSignVerifyDetached(signature, message, message.length,
                    key.toByteArray());
        }
    }

    @Nested
    class Random {
        @ParameterizedTest
        @ValueSource(ints = { 4, 8, 16, 24, 32, 64 })
        void shouldGenerateRandomBytesOfCorrectLength(final int len) {
            assertEquals(len, Crypto.randomBytes(len).length);
        }

        @ParameterizedTest
        @ValueSource(ints = { 4, 8, 16, 24, 32, 64 })
        void shouldGenerateRandomStringsOfCorrectLength(final int len) {
            assertEquals(len * 2, Crypto.randomString(len).length());
        }

        @ParameterizedTest
        @ValueSource(ints = { 4, 8, 16, 24, 32, 64 })
        void shouldGenerateRandomNumberOfCorrectSize(final int size) {
            final int number = Crypto.randomNumber(size);

            assertTrue(number > -1, "Number " + number + " should be positive.");
            assertTrue(number <= size, "Number " + number + " should be smaller than or equals to " + size + ".");
        }
    }

    // We've to wrap the SodiumJava, because Mockito does not support native calls
    static class UnitSodium extends DrasylSodiumJava {
        @Override
        public int crypto_kx_keypair(final byte[] publicKey, final byte[] secretKey) {
            return 0;
        }
    }
}

