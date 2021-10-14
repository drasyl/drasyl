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
package org.drasyl.crypto.sodium;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.drasyl.crypto.CryptoException;

public class LazyDrasylSodium {
    public static final short ED25519_PUBLICKEYBYTES = 32;
    public static final short ED25519_SECRETKEYBYTES = 64;
    public static final short ED25519_BYTES = 64;
    public static final short CURVE25519_PUBLICKEYBYTES = 32;
    public static final short CURVE25519_SECRETKEYBYTES = 32;
    public static final short SESSIONKEYBYTES = 32;
    public static final short XCHACHA20POLY1305_IETF_ABYTES = 16;
    private final DrasylSodium sodium;

    public LazyDrasylSodium(final DrasylSodium sodium) {
        this.sodium = sodium;
    }

    /**
     * Generate a signing keypair (ed25519).
     *
     * @param publicKey Public key.
     * @param secretKey Secret key.
     * @return True if successful.
     */
    public boolean cryptoSignKeypair(final byte[] publicKey, final byte[] secretKey) {
        return successful(getSodium().crypto_sign_keypair(publicKey, secretKey));
    }

    /**
     * Converts a public ed25519 key to a public curve25519 key.
     *
     * @param curve The array in which the generated key will be placed.
     * @param ed    The public key in ed25519.
     * @return Return true if the conversion was successful.
     */
    public boolean convertPublicKeyEd25519ToCurve25519(final byte[] curve, final byte[] ed) {
        return successful(getSodium().crypto_sign_ed25519_pk_to_curve25519(curve, ed));
    }

    /**
     * Converts a secret ed25519 key to a secret curve25519 key.
     *
     * @param curve The array in which the generated key will be placed.
     * @param ed    The secret key in ed25519.
     * @return Return true if the conversion was successful.
     */
    public boolean convertSecretKeyEd25519ToCurve25519(final byte[] curve, final byte[] ed) {
        return successful(getSodium().crypto_sign_ed25519_sk_to_curve25519(curve, ed));
    }

    /**
     * This function computes a pair of shared keys (rx and tx) using the client's public key
     * clientPk, the server's secret key serverSk and the server's public key serverPk.
     *
     * @param serverPk Server public key of size {@link #CURVE25519_PUBLICKEYBYTES}.
     * @param serverSk Server secret key of size {@link #CURVE25519_SECRETKEYBYTES}.
     * @param clientPk Client public key of size {@link #CURVE25519_PUBLICKEYBYTES}.
     * @return True if successful or false if the client public key is wrong.
     */
    public SessionPair cryptoKxServerSessionKeys(final byte[] serverPk,
                                                 final byte[] serverSk,
                                                 final byte[] clientPk) throws CryptoException {
        final byte[] rx = new byte[SESSIONKEYBYTES];
        final byte[] tx = new byte[SESSIONKEYBYTES];

        if (!successful(getSodium().crypto_kx_server_session_keys(rx, tx, serverPk, serverSk, clientPk))) {
            throw new CryptoException("Failed creating server session keys.");
        }

        return SessionPair.of(rx, tx);
    }

    /**
     * This function computes a pair of shared keys (rx and tx) using the client's public key
     * clientPk, the client's secret key clientSk and the server's public key serverPk.
     *
     * @param clientPk Client public key of size {@link #CURVE25519_PUBLICKEYBYTES}.
     * @param clientSk Client secret key of size {@link #CURVE25519_SECRETKEYBYTES}.
     * @param serverPk Server public key of size {@link #CURVE25519_PUBLICKEYBYTES}.
     * @return True if successful or false if the server public key is wrong.
     */
    public SessionPair cryptoKxClientSessionKeys(final byte[] clientPk,
                                                 final byte[] clientSk,
                                                 final byte[] serverPk) throws CryptoException {
        final byte[] rx = new byte[SESSIONKEYBYTES];
        final byte[] tx = new byte[SESSIONKEYBYTES];

        if (!successful(getSodium().crypto_kx_client_session_keys(rx, tx, clientPk, clientSk, serverPk))) {
            throw new CryptoException("Failed creating client session keys.");
        }

        return SessionPair.of(rx, tx);
    }

    public boolean cryptoAeadXChaCha20Poly1305IetfEncrypt(final byte[] c,
                                                          final long[] cLen,
                                                          final byte[] m,
                                                          final long mLen,
                                                          final byte[] ad,
                                                          final long adLen,
                                                          final byte[] nSec,
                                                          final byte[] nPub,
                                                          final byte[] k) {
        return successful(getSodium().crypto_aead_xchacha20poly1305_ietf_encrypt(c, cLen, m, mLen, ad, adLen, nSec, nPub, k));
    }

    public boolean cryptoAeadXChaCha20Poly1305IetfDecrypt(final byte[] m,
                                                          final long[] mLen,
                                                          final byte[] nSec,
                                                          final byte[] c,
                                                          final long cLen,
                                                          final byte[] ad,
                                                          final long adLen,
                                                          final byte[] nPub,
                                                          final byte[] k) {
        return successful(getSodium().crypto_aead_xchacha20poly1305_ietf_decrypt(m, mLen, nSec, c, cLen, ad, adLen, nPub, k));
    }

    /**
     * Returns a signature for a message. This does not prepend the signature to the message.
     *
     * @param signature  The signature will be added to this byte array.
     * @param message    The message to sign.
     * @param messageLen The message length.
     * @param secretKey  The secret key.
     * @return True if the secret key could provide a signature.
     */
    public boolean cryptoSignDetached(final byte[] signature,
                                      final byte[] message,
                                      final long messageLen,
                                      final byte[] secretKey) {
        if (messageLen < 0 || messageLen > message.length) {
            throw new IllegalArgumentException("messageLen out of bounds: " + messageLen);
        }
        return successful(getSodium().crypto_sign_detached(signature, (new PointerByReference(Pointer.NULL)).getPointer(), message, messageLen, secretKey));
    }

    /**
     * Verifies that {@code signature} is valid for the {@code message}.
     *
     * @param signature  The signature.
     * @param message    The message.
     * @param messageLen The message length.
     * @param publicKey  The public key that signed the message.
     * @return Returns true if the signature is valid for the message.
     * @see #cryptoSignDetached(byte[], byte[], long, byte[])
     */
    public boolean cryptoSignVerifyDetached(final byte[] signature,
                                            final byte[] message,
                                            final int messageLen,
                                            final byte[] publicKey) {
        if (messageLen < 0 || messageLen > message.length) {
            throw new IllegalArgumentException("messageLen out of bounds: " + messageLen);
        }
        return successful(getSodium().crypto_sign_verify_detached(signature, message, messageLen, publicKey));
    }

    /**
     * Evaluates the return value of a native sodium function call.
     *
     * @param res the result of the function call
     * @return true if call was successful, otherwise false
     */
    public boolean successful(final int res) {
        return (res == 0);
    }

    public DrasylSodium getSodium() {
        return sodium;
    }
}
