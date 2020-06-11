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
package org.drasyl.peer.connection.handler;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.SignedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignatureHandlerTest {
    private Identity identity;
    private AttributeKey<Identity> attributeKey;

    @BeforeEach
    void setUp() throws CryptoException {
        attributeKey = AttributeKey.valueOf("identity");

        // generate valid key pair
        KeyPair keyPair = Crypto.generateKeys();
        identity = Identity.of(CompressedKeyPair.of(keyPair));
    }

    @Test
    void shouldSignUnsignedMessage() throws CryptoException {
        SignatureHandler handler = new SignatureHandler(identity);
        QuitMessage message = new QuitMessage();

        EmbeddedChannel channel = new EmbeddedChannel(handler);
        assertTrue(channel.writeOutbound(message));

        SignedMessage signedMessage = channel.readOutbound();

        assertTrue(Crypto.verifySignature(signedMessage.getKid().toUncompressedKey(), signedMessage));
        assertEquals(identity.getPublicKey(), signedMessage.getKid());
    }

    @Test
    void shouldThrowExceptionIfKeyCantBeRead() throws CryptoException {
        CompressedPrivateKey mockedPrivateKey = mock(CompressedPrivateKey.class);
        Identity mockedIdentity = mock(Identity.class);
        when(mockedIdentity.getPublicKey()).thenReturn(identity.getPublicKey());
        when(mockedIdentity.getPrivateKey()).thenReturn(mockedPrivateKey);
        when(mockedPrivateKey.toUncompressedKey()).thenThrow(CryptoException.class);

        SignatureHandler handler = new SignatureHandler(mockedIdentity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        QuitMessage message = new QuitMessage();

        assertThrows(CryptoException.class, () -> channel.writeOutbound(message));
    }

    @Test
    void shouldVerifyIncomingMessage() throws CryptoException {
        SignatureHandler handler = new SignatureHandler(identity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        KeyPair keyPair = Crypto.generateKeys();
        Identity identity2 = Identity.of(CompressedKeyPair.of(keyPair));
        channel.attr(attributeKey).set(identity2);

        QuitMessage message = new QuitMessage();
        SignedMessage signedMessage = new SignedMessage(message, identity2.getPublicKey());
        Crypto.sign(identity2.getPrivateKey().toUncompressedKey(), signedMessage);

        assertNotNull(signedMessage.getKid());
        assertNotNull(signedMessage.getSignature());

        assertTrue(channel.writeInbound(signedMessage));
        QuitMessage out = channel.readInbound();

        assertEquals(message, out);
    }

    @Test
    void shouldNotPassthroughsMessageWhenSignatureIsInvalid() throws CryptoException {
        SignatureHandler handler = new SignatureHandler(identity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        KeyPair keyPair = Crypto.generateKeys();
        Identity identity2 = Identity.of(CompressedKeyPair.of(keyPair));
        channel.attr(attributeKey).set(identity2);

        QuitMessage message = new QuitMessage();
        SignedMessage signedMessage = new SignedMessage(message, identity2.getPublicKey());
        Crypto.sign(identity.getPrivateKey().toUncompressedKey(), signedMessage);

        assertNotNull(signedMessage.getKid());
        assertNotNull(signedMessage.getSignature());

        assertFalse(channel.writeInbound(signedMessage));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsMessageWhenPublicKeyCantBeExtracted() throws CryptoException {
        CompressedPublicKey mockedPublicKey = mock(CompressedPublicKey.class);
        when(mockedPublicKey.toUncompressedKey()).thenThrow(CryptoException.class);

        SignatureHandler handler = new SignatureHandler(identity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        QuitMessage message = new QuitMessage();

        assertFalse(channel.writeInbound(message));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsWhenMessageIsNotSigned() {
        SignatureHandler handler = new SignatureHandler(identity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        QuitMessage message = new QuitMessage();

        assertFalse(channel.writeInbound(message));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsMessageWhenKeysNotIdenticallyToChannelKey() throws CryptoException {
        SignatureHandler handler = new SignatureHandler(identity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.attr(attributeKey).set(identity);

        KeyPair keyPair = Crypto.generateKeys();
        Identity identity2 = Identity.of(CompressedKeyPair.of(keyPair));

        QuitMessage message = new QuitMessage();
        SignedMessage signedMessage = new SignedMessage(message, identity2.getPublicKey());
        Crypto.sign(identity2.getPrivateKey().toUncompressedKey(), signedMessage);

        assertNotNull(signedMessage.getKid());
        assertNotNull(signedMessage.getSignature());

        assertFalse(channel.writeInbound(signedMessage));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsMessageWhenPublicKeyCantBeExtracted2() throws CryptoException {
        CompressedPublicKey mockedPublicKey = mock(CompressedPublicKey.class);
        when(mockedPublicKey.toUncompressedKey()).thenThrow(CryptoException.class);

        SignatureHandler handler = new SignatureHandler(identity);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        QuitMessage message = new QuitMessage();
        SignedMessage signedMessage = new SignedMessage(message, mockedPublicKey);
        signedMessage.setSignature(mock(Signature.class));

        assertFalse(channel.writeInbound(signedMessage));
        assertNull(channel.readInbound());
    }
}