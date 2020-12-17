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
package org.drasyl.remote.message;

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UniteMessageTest {
    private final int networkId = 1;
    CompressedPublicKey sender;
    ProofOfWork proofOfWork;
    CompressedPublicKey recipient;
    private CompressedPublicKey publicKey;
    private InetSocketAddress address;

    @BeforeEach
    void setUp() throws CryptoException {
        sender = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        proofOfWork = ProofOfWork.of(6657650);
        recipient = CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");
        publicKey = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
        address = InetSocketAddress.createUnresolved("127.0.0.1", 12345);
    }

    @Nested
    class Serialisation {
        @Test
        void shouldSerialiseCorrectly() throws Exception {
            final UniteMessage message = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, new InetSocketAddress("127.0.0.1", 12345));
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            final ByteBufOutputStream out = new ByteBufOutputStream(byteBuf);

            message.getPublicHeader().writeDelimitedTo(out);
            message.getPrivateHeader().writeDelimitedTo(out);
            message.getBody().writeDelimitedTo(out);

            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(byteBuf);
            final UniteMessage encoded = new UniteMessage(envelope.getPublicHeader(), (Protocol.Unite) envelope.getBody());

            assertEquals(message, encoded);
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldNotBeNull() {
            final UniteMessage message = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 12345));

            assertNotNull(message.toString());
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new UniteMessage(networkId, null, proofOfWork, recipient, publicKey, address), "Message requires a sender");

            assertThrows(NullPointerException.class, () -> new UniteMessage(networkId, sender, null, recipient, publicKey, address), "Message requires a proofOfWork");

            assertThrows(NullPointerException.class, () -> new UniteMessage(networkId, sender, proofOfWork, null, publicKey, address), "Message requires a recipient");

            assertThrows(NullPointerException.class, () -> new UniteMessage(networkId, sender, proofOfWork, recipient, null, address), "Message requires a publicKey");

            assertThrows(NullPointerException.class, () -> new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, null), "Message requires a address");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentAddress() {
            final UniteMessage message1 = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 12345));
            final UniteMessage message2 = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 12345));
            final UniteMessage message3 = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 23456));

            assertEquals(message1, message2);
            assertEquals(message1.getAddress(), message2.getAddress());
            assertEquals(message1.getPublicKey(), message2.getPublicKey());
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentAddress() {
            final UniteMessage message1 = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 12345));
            final UniteMessage message2 = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 12345));
            final UniteMessage message3 = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, InetSocketAddress.createUnresolved("127.0.0.1", 23456));

            assertEquals(message1.hashCode(), message2.hashCode());
            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}