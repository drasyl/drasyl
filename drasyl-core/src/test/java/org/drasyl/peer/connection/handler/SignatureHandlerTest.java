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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ExceptionMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.SignedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.peer.connection.handler.ThreeWayHandshakeClientHandler.ATTRIBUTE_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.ExceptionMessage.Error.ERROR_FORMAT;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureHandlerTest {
    private Identity identity;
    @Mock
    private CompressedPrivateKey mockedPrivateKey;
    @Mock
    private Identity mockedIdentity;
    @Mock
    private CompressedPublicKey mockedPublicKey;
    @Mock
    private Signature signature;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;

    @BeforeEach
    void setUp() throws CryptoException {
        identity = Identity.of(ProofOfWork.of(16425882), "030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3", "05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34");
    }

    @Test
    void shouldSignUnsignedMessage() throws CryptoException {
        final SignatureHandler handler = new SignatureHandler(identity);
        final QuitMessage message = new QuitMessage(sender, proofOfWork, recipient, REASON_SHUTTING_DOWN);

        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        assertTrue(channel.writeOutbound(message));

        final SignedMessage signedMessage = channel.readOutbound();

        assertTrue(Crypto.verifySignature(signedMessage.getSender().toUncompressedKey(), signedMessage));
        assertEquals(identity.getPublicKey(), signedMessage.getSender());
    }

    @Test
    void shouldThrowExceptionIfKeyCantBeRead() throws CryptoException {
        when(mockedIdentity.getPublicKey()).thenReturn(identity.getPublicKey());
        when(mockedIdentity.getProofOfWork()).thenReturn(identity.getProofOfWork());
        when(mockedIdentity.getPrivateKey()).thenReturn(mockedPrivateKey);
        when(mockedPrivateKey.toUncompressedKey()).thenThrow(CryptoException.class);

        final SignatureHandler handler = new SignatureHandler(mockedIdentity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        final QuitMessage message = new QuitMessage(sender, proofOfWork, recipient, REASON_SHUTTING_DOWN);

        assertThrows(CryptoException.class, () -> channel.writeOutbound(message));
    }

    @Test
    void shouldVerifyIncomingMessage() throws CryptoException {
        final SignatureHandler handler = new SignatureHandler(identity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final CompressedPublicKey identity2 = CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458");
        channel.attr(ATTRIBUTE_PUBLIC_KEY).set(identity2);

        final QuitMessage message = new QuitMessage(sender, proofOfWork, recipient, REASON_SHUTTING_DOWN);
        final SignedMessage signedMessage = new SignedMessage(identity2, proofOfWork, message);

        final Identity privateIdentity2 = Identity.of(ProofOfWork.of(36558946), "0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458", "00ea42e42240e0f6e0f9bee7058118aa149ce72de25cde574523ff9199ec2660");
        Crypto.sign(privateIdentity2.getPrivateKey().toUncompressedKey(), signedMessage);

        assertNotNull(signedMessage.getSender());
        assertNotNull(signedMessage.getSignature());

        assertTrue(channel.writeInbound(signedMessage));
        final QuitMessage out = channel.readInbound();

        assertEquals(message, out);
    }

    @Test
    void shouldNotPassthroughsMessageWhenSignatureIsInvalid() throws CryptoException {
        final SignatureHandler handler = new SignatureHandler(identity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        final CompressedPublicKey identity2 = CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13");
        channel.attr(ATTRIBUTE_PUBLIC_KEY).set(identity2);

        final QuitMessage message = new QuitMessage(sender, proofOfWork, recipient, REASON_SHUTTING_DOWN);
        final SignedMessage signedMessage = new SignedMessage(identity2, proofOfWork, message);
        Crypto.sign(identity.getPrivateKey().toUncompressedKey(), signedMessage);

        assertNotNull(signedMessage.getSender());
        assertNotNull(signedMessage.getSignature());

        assertFalse(channel.writeInbound(signedMessage));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsMessageWhenPublicKeyCantBeExtracted() {
        final SignatureHandler handler = new SignatureHandler(identity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        final Message message = new ExceptionMessage(sender, proofOfWork, ERROR_FORMAT);

        assertFalse(channel.writeInbound(message));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsWhenMessageIsNotSigned() {
        final SignatureHandler handler = new SignatureHandler(identity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        final Message message = new ExceptionMessage(sender, proofOfWork, ERROR_FORMAT);

        assertFalse(channel.writeInbound(message));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsMessageWhenKeysNotIdenticallyToChannelKey() throws CryptoException {
        final SignatureHandler handler = new SignatureHandler(identity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.attr(ATTRIBUTE_PUBLIC_KEY).set(identity.getPublicKey());

        final CompressedPublicKey identity2 = CompressedPublicKey.of("026786e52addf59f0e40d5f6a4c1d2873afc04a6460a85b0becd04eb86f1e7116d");

        final QuitMessage message = new QuitMessage(sender, proofOfWork, recipient, REASON_SHUTTING_DOWN);
        final SignedMessage signedMessage = new SignedMessage(identity2, proofOfWork, message);

        final Identity privateIdentity2 = Identity.of(ProofOfWork.of(2096201), "026786e52addf59f0e40d5f6a4c1d2873afc04a6460a85b0becd04eb86f1e7116d", "02c43ebf22f27add698de3d5a534d4df88616b5acf164850aa56b7f4e8dbfbe2");
        Crypto.sign(privateIdentity2.getPrivateKey().toUncompressedKey(), signedMessage);

        assertNotNull(signedMessage.getSender());
        assertNotNull(signedMessage.getSignature());

        assertFalse(channel.writeInbound(signedMessage));
        assertNull(channel.readInbound());
    }

    @Test
    void shouldNotPassthroughsMessageWhenPublicKeyCantBeExtracted2() throws CryptoException {
        when(mockedPublicKey.toUncompressedKey()).thenThrow(CryptoException.class);

        final SignatureHandler handler = new SignatureHandler(identity);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        final QuitMessage message = new QuitMessage(sender, proofOfWork, recipient, REASON_SHUTTING_DOWN);
        final SignedMessage signedMessage = new SignedMessage(mockedPublicKey, proofOfWork, message);
        signedMessage.setSignature(signature);

        assertFalse(channel.writeInbound(signedMessage));
        assertNull(channel.readInbound());
    }
}