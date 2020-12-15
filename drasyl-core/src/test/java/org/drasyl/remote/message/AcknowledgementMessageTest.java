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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AcknowledgementMessageTest {
    private final MessageId correspondingId = MessageId.of("412176952b5b81fd13f84a7c");
    private CompressedPublicKey sender;
    private ProofOfWork proofOfWork;
    private CompressedPublicKey recipient;

    @BeforeEach
    void setUp() throws CryptoException {
        sender = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
        recipient = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        proofOfWork = ProofOfWork.of(1);
    }

    @Nested
    class Serialisation {
        @Test
        void shouldSerialiseCorrectly() throws Exception {
            final AcknowledgementMessage message = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            final ByteBufOutputStream out = new ByteBufOutputStream(byteBuf);

            message.getPublicHeader().writeDelimitedTo(out);
            message.getPrivateHeader().writeDelimitedTo(out);
            message.getBody().writeDelimitedTo(out);

            final IntermediateEnvelope envelope = IntermediateEnvelope.of(byteBuf);
            final AcknowledgementMessage encoded = new AcknowledgementMessage(envelope.getPublicHeader(), (Protocol.Acknowledgement) envelope.getBody());

            assertEquals(message, encoded);
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldNotBeNull() {
            final AcknowledgementMessage message = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);

            assertNotNull(message.toString());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            final AcknowledgementMessage message1 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);
            final AcknowledgementMessage message2 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);

            assertEquals(message1, message2);
            assertEquals(message1.getCorrespondingId(), message2.getCorrespondingId());
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final AcknowledgementMessage message1 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);
            final AcknowledgementMessage message2 = new AcknowledgementMessage(1337, sender, proofOfWork, recipient, correspondingId);

            assertEquals(message1.hashCode(), message2.hashCode());
        }
    }
}