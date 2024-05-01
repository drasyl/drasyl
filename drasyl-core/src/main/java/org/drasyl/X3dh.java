/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl;

import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.protocol.Nonce;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.KeyAgreementPublicKey;
import org.drasyl.identity.KeyAgreementSecretKey;
import org.drasyl.identity.KeyPair;
import org.drasyl.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class X3dh {
    public static final Crypto CRYPTO = Crypto.INSTANCE;

    public static void main(String[] args) throws CryptoException {
        final Server server = new Server();
        final Client alice = new Client(Identity.of(-2082598243,
                "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
                "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127"), server);
        final Client bob = new Client(Identity.of(-2122268831,
                "622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e",
                "fc10ab6bb85c51c453dbfe44c0c29d96d1a365257ad871dea49c29c98f1f8421622d860a23517b0e20e59d8a481db4da2c89649c979d7318bc4ef19828f4663e"), server);

        // both clients store a public ephemeral key at servers, alongside with a signature that this key belongs to them
        alice.sendHello(server.identityPublicKey(), alice.sessionKeyPairPublicKey(), alice.sessionKeyPairPublicKeySignature());
        bob.sendHello(server.identityPublicKey(), bob.sessionKeyPairPublicKey(), bob.sessionKeyPairPublicKeySignature());

        // one client initiates X3dh by sending an empty app msg for a peer to the server
        alice.sendEmptyApp(server.identityPublicKey(), bob.identityPublicKey());

        // server will then forward stored keys allowing both clients to agree on keys for rx/tx

        // send encrypted messages. just for verification
        final Nonce aliceNonce = Nonce.randomNonce();
        final byte[] encryptedMsgToBob = CRYPTO.encrypt("Hello Bob".getBytes(), aliceNonce.toByteArray(), aliceNonce, alice.sessionPair);
        System.out.println(new String(CRYPTO.decrypt(encryptedMsgToBob, aliceNonce.toByteArray(), aliceNonce, bob.sessionPair)));

        final Nonce bobNonce = Nonce.randomNonce();
        final byte[] encryptedMsgToAlice = CRYPTO.encrypt("Hello Alice".getBytes(), bobNonce.toByteArray(), bobNonce, bob.sessionPair);
        System.out.println(new String(CRYPTO.decrypt(encryptedMsgToAlice, bobNonce.toByteArray(), bobNonce, alice.sessionPair)));
    }

    static class Server {
        final Identity identity = Identity.of(-2142814279,
                "f43772fd65e9fa28e729c71c199ef21c7f2b019be924e87f94f3dc27e9e63853",
                "c0b360e58d296c2e39c44ef67d07ff9150d072c7e87cda7e8ef9ffe516ce2574f43772fd65e9fa28e729c71c199ef21c7f2b019be924e87f94f3dc27e9e63853");
        final Map<IdentityPublicKey, Client> clients = new HashMap<>();
        final Map<IdentityPublicKey, Pair<KeyAgreementPublicKey, byte[]>> sessionKeys = new HashMap<>();

        private IdentitySecretKey identitySecretKey() {
            return identity.getIdentitySecretKey();
        }

        public IdentityPublicKey identityPublicKey() {
            return identity.getIdentityPublicKey();
        }

        public void receiveHello(final Client client,
                                 final KeyAgreementPublicKey sessionKeyPairPublicKey,
                                 final byte[] sessionKeyPairPublicKeySignature) {
            clients.put(client.identityPublicKey(), client);
            sessionKeys.put(client.identityPublicKey(), Pair.of(sessionKeyPairPublicKey, sessionKeyPairPublicKeySignature));
        }

        public void receiveEmptyApp(final Client requester,
                                    final IdentityPublicKey responderIdentityPublicKey) throws CryptoException {
            final IdentityPublicKey requesterIdentityPublicKey = requester.identityPublicKey();
            final Pair<KeyAgreementPublicKey, byte[]> requesterPair = sessionKeys.get(requesterIdentityPublicKey);
            final KeyAgreementPublicKey requesterSessionKeyPairPublicKey = requesterPair.first();
            final byte[] requesterSessionKeyPairPublicKeySignature = requesterPair.second();

            final Client responder = clients.get(responderIdentityPublicKey);
            final Pair<KeyAgreementPublicKey, byte[]> responderPair = sessionKeys.get(responderIdentityPublicKey);
            final KeyAgreementPublicKey responderSessionKeyPairPublicKey = responderPair.first();
            final byte[] responderSessionKeyPairPublicKeySignature = responderPair.second();

            sendUnite(requester, responderIdentityPublicKey, requesterSessionKeyPairPublicKey, responderSessionKeyPairPublicKey, responderSessionKeyPairPublicKeySignature);
            sendUnite(responder, requesterIdentityPublicKey, responderSessionKeyPairPublicKey, requesterSessionKeyPairPublicKey, requesterSessionKeyPairPublicKeySignature);
        }

        private void sendUnite(final Client client,
                               final IdentityPublicKey peerIdentityPublicKey,
                               final KeyAgreementPublicKey sessionKeyPairPublicKey,
                               final KeyAgreementPublicKey peerSessionKeyPairPublicKey,
                               final byte[] peerSessionKeyPairPublicKeySignature) throws CryptoException {
            client.receiveUnite(peerIdentityPublicKey, sessionKeyPairPublicKey, peerSessionKeyPairPublicKey, peerSessionKeyPairPublicKeySignature);
        }
    }

    static class Client {
        final Identity identity;
        private final Server server;
        final KeyPair<KeyAgreementPublicKey, KeyAgreementSecretKey> sessionKeyPair;
        private SessionPair sessionPair;

        Client(final Identity identity, final Server server) throws CryptoException {
            this.identity = identity;
            this.server = server;
            this.sessionKeyPair = CRYPTO.generateEphemeralKeyPair();
        }

        private IdentitySecretKey identitySecretKey() {
            return identity.getIdentitySecretKey();
        }

        public IdentityPublicKey identityPublicKey() {
            return identity.getIdentityPublicKey();
        }

        public KeyAgreementPublicKey sessionKeyPairPublicKey() {
            return sessionKeyPair.getPublicKey();
        }

        public byte[] sessionKeyPairPublicKeySignature() throws CryptoException {
            return CRYPTO.sign(sessionKeyPairPublicKey().toByteArray(), identitySecretKey());
        }

        private KeyAgreementSecretKey sessionKeyPairSecretKey() {
            return sessionKeyPair.getSecretKey();
        }

        public void sendHello(final IdentityPublicKey serverIdentityPublicKey,
                              final KeyAgreementPublicKey sessionKeyPairPublicKey,
                              final byte[] sessionKeyPairPublicKeySignature) throws CryptoException {
            server.receiveHello(this, sessionKeyPairPublicKey, sessionKeyPairPublicKeySignature);
        }

        public void sendEmptyApp(final IdentityPublicKey serverIdentityPublicKey,
                                 final IdentityPublicKey peerPublicKey) throws CryptoException {
            server.receiveEmptyApp(this, peerPublicKey);
        }

        public void receiveUnite(final IdentityPublicKey peerIdentityPublicKey,
                                 final KeyAgreementPublicKey sessionKeyPairPublicKey,
                                 final KeyAgreementPublicKey peerSessionKeyPairPublicKey,
                                 final byte[] peerSessionKeyPairPublicKeySignature) throws CryptoException {
            // verify
            if (!Objects.equals(sessionKeyPairPublicKey(), sessionKeyPairPublicKey)) {
                throw new RuntimeException("not my key");
            }
            if (!CRYPTO.verifySignature(peerSessionKeyPairPublicKeySignature, peerSessionKeyPairPublicKey.toByteArray(), peerIdentityPublicKey)) {
                throw new RuntimeException("invalid peer key");
            }

            // generate
            sessionPair = CRYPTO.generateSessionKeyPair(sessionKeyPair, peerSessionKeyPairPublicKey);
        }
    }
}
