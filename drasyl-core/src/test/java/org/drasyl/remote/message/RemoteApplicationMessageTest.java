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
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RemoteApplicationMessageTest {
    private final int networkId = 1;
    CompressedPublicKey sender;
    ProofOfWork proofOfWork;
    CompressedPublicKey recipient;
    private MessageId id;
    private byte hopCount;
    @Mock
    private UserAgent userAgent;

    @BeforeEach
    void setUp() throws CryptoException {
        sender = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
        proofOfWork = ProofOfWork.of(6657650);
        recipient = CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55");
        id = MessageId.of("89ba3cd9efb7570eb3126d11");
        hopCount = 64;
    }

    @Nested
    class Serialisation {
        @Test
        void shouldSerialiseCorrectly() throws Exception {
            final RemoteApplicationMessage message = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            final ByteBufOutputStream out = new ByteBufOutputStream(byteBuf);

            message.getPublicHeader().writeDelimitedTo(out);
            message.getPrivateHeader().writeDelimitedTo(out);
            message.getBody().writeDelimitedTo(out);

            final IntermediateEnvelope envelope = IntermediateEnvelope.of(byteBuf);
            final RemoteApplicationMessage encoded = new RemoteApplicationMessage(envelope.getPublicHeader(), (Protocol.Application) envelope.getBody());

            assertEquals(message, encoded);
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldNotBeNull() {
            final RemoteApplicationMessage message = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
            });

            assertNotNull(message.toString());
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, null, proofOfWork, recipient, new byte[]{}), "Message requires a sender");

            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, sender, proofOfWork, null, new byte[]{}), "Message requires a recipient");

            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, null), "Message requires a payload");

            assertThrows(NullPointerException.class, () -> new RemoteApplicationMessage(networkId, null, proofOfWork, null, null), "Message requires a sender, a recipient and a payload");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final RemoteApplicationMessage message1 = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message2 = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message3 = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{
                    0x03,
                    0x02,
                    0x01
            });

            assertEquals(message1, message2);
            assertEquals(message1.getType(), message2.getType());
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPayload() {
            final RemoteApplicationMessage message1 = new RemoteApplicationMessage(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount, new Signature(new byte[]{}), byte[].class.getName(), new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message2 = new RemoteApplicationMessage(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount, new Signature(new byte[]{}), byte[].class.getName(), new byte[]{
                    0x00,
                    0x01,
                    0x02
            });
            final RemoteApplicationMessage message3 = new RemoteApplicationMessage(id, userAgent, networkId, sender, proofOfWork, recipient, hopCount, new Signature(new byte[]{}), byte[].class.getName(), new byte[]{
                    0x03,
                    0x02,
                    0x01
            });

            assertEquals(message1.hashCode(), message2.hashCode());
            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }

    @Nested
    class IncrementHopCount {
        @Test
        void shouldIncrementHopCountByOne() {
            final RemoteApplicationMessage message = new RemoteApplicationMessage(networkId, sender, proofOfWork, recipient, new byte[]{});

            message.incrementHopCount();

            assertEquals(1, message.getHopCount());
        }
    }
}