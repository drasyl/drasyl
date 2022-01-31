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

/**
 * This class presents a restricted view to the native sodium library. Only the required functions
 * for drasyl are considered.
 */
public class Sodium {
    protected Sodium() {
    }

    protected void register() {
        if (sodium_init() != 0) {
            throw new IllegalStateException("Could not initialize sodium library properly.");
        }
    }

    /*
     * UTILS
     */
    public native int sodium_init();

    /*
     * HASHING
     */
    public native int crypto_hash_sha256(byte[] out, byte[] in, long inLen);

    /*
     * SIGNING
     */
    public native int crypto_sign_keypair(byte[] publicKey, byte[] secretKey);

    public native int crypto_sign_ed25519_pk_to_curve25519(
            byte[] curve25519PublicKey,
            byte[] ed25519PublicKey
    );

    public native int crypto_sign_ed25519_sk_to_curve25519(
            byte[] curve25519SecretKey,
            byte[] ed25519SecretKey
    );

    public native int crypto_sign_detached(
            byte[] signature,
            Pointer sigLength,
            byte[] message,
            long messageLen,
            byte[] secretKey
    );

    public native int crypto_sign_verify_detached(byte[] signature,
                                                  byte[] message,
                                                  long messageLen,
                                                  byte[] publicKey);

    /*
     * KEY EXCHANGE
     */
    public native int crypto_kx_keypair(byte[] publicKey, byte[] secretKey);

    public native int crypto_kx_client_session_keys(
            byte[] rx,
            byte[] tx,
            byte[] clientPk,
            byte[] clientSk,
            byte[] serverPk
    );

    public native int crypto_kx_server_session_keys(
            byte[] rx,
            byte[] tx,
            byte[] serverPk,
            byte[] serverSk,
            byte[] clientPk
    );

    /*
     * XCHACHA
     */
    public native int crypto_aead_xchacha20poly1305_ietf_encrypt(
            byte[] c,
            long[] cLen,
            byte[] m,
            long mLen,
            byte[] ad,
            long adLen,
            byte[] nSec,
            byte[] nPub,
            byte[] k
    );

    public native int crypto_aead_xchacha20poly1305_ietf_decrypt(
            byte[] m,
            long[] mLen,
            byte[] nSec,
            byte[] c,
            long cLen,
            byte[] ad,
            long adLen,
            byte[] nPub,
            byte[] k
    );
}
