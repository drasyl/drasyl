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
package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPublicKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.KeyPair;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SignedMessageTest {
    private static KeyPair keyPair;
    @Mock
    private CompressedPublicKey publicKey;

    @BeforeAll
    static void setUp() throws CryptoException {
        keyPair = CompressedKeyPair.of("0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c", "00b96ac2757f5f427a210c7a68f357bfa03f986b547a3b68e0bf79daa45f9edd").toUncompressedKeyPair();
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            String json = "{\"@type\":\"SignedMessage\",\"payload\":{\"@type\":\"PingMessage\",\"id\":\"05155f021bed10398c4ca71c\"},\"kid\":\"0313c96bed7252c22218972dd21d611fec413d76e9eaac2717ed76889dcd357edf\",\"signature\":{\"bytes\":\"MEQCIAESpHbepeb9cRDA5Hd0GErCQnpSj+GN2bQIFEO2AgN5AiAGJwpL9G2BEki8c+VdCcnvKloYXKCDYJSTYt3e5VTylw==\"}}\n";

            assertEquals(new SignedMessage(new PingMessage(), CompressedPublicKey.of("0313c96bed7252c22218972dd21d611fec413d76e9eaac2717ed76889dcd357edf"), new Signature(new byte[]{
                    0x30,
                    0x44,
                    0x02,
                    0x20,
                    0x01,
                    0x12,
                    (byte) 0xa4,
                    0x76,
                    (byte) 0xde,
                    (byte) 0xa5,
                    (byte) 0xe6,
                    (byte) 0xfd,
                    0x71,
                    0x10,
                    (byte) 0xc0,
                    (byte) 0xe4,
                    0x77,
                    0x74,
                    0x18,
                    0x4a,
                    (byte) 0xc2,
                    0x42,
                    0x7a,
                    0x52,
                    (byte) 0x8f,
                    (byte) 0xe1,
                    (byte) 0x8d,
                    (byte) 0xd9,
                    (byte) 0xb4,
                    0x08,
                    0x14,
                    0x43,
                    (byte) 0xb6,
                    0x02,
                    0x03,
                    0x79,
                    0x02,
                    0x20,
                    0x06,
                    0x27,
                    0x0a,
                    0x4b,
                    (byte) 0xf4,
                    0x6d,
                    (byte) 0x81,
                    0x12,
                    0x48,
                    (byte) 0xbc,
                    0x73,
                    (byte) 0xe5,
                    0x5d,
                    0x09,
                    (byte) 0xc9,
                    (byte) 0xef,
                    0x2a,
                    0x5a,
                    0x18,
                    0x5c,
                    (byte) 0xa0,
                    (byte) 0x83,
                    0x60,
                    (byte) 0x94,
                    (byte) 0x93,
                    0x62,
                    (byte) 0xdd,
                    (byte) 0xde,
                    (byte) 0xe5,
                    0x54,
                    (byte) 0xf2,
                    (byte) 0x97
            })), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            String json = "{\"@type\":\"SignedMessage\",\"kid\":\"0313c96bed7252c22218972dd21d611fec413d76e9eaac2717ed76889dcd357edf\",\"signature\":{\"bytes\":\"MEQCIAESpHbepeb9cRDA5Hd0GErCQnpSj+GN2bQIFEO2AgN5AiAGJwpL9G2BEki8c+VdCcnvKloYXKCDYJSTYt3e5VTylw==\"}}\n";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            SignedMessage message = new SignedMessage(new PingMessage(), publicKey);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", SignedMessage.class.getSimpleName())
                    .containsKeys("payload", "kid", "signature");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() throws CryptoException {
            PingMessage message = new PingMessage();
            SignedMessage signedMessage1 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));
            SignedMessage signedMessage2 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));

            assertEquals(signedMessage1, signedMessage2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() throws CryptoException {
            PingMessage message = new PingMessage();
            SignedMessage signedMessage1 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));
            SignedMessage signedMessage2 = new SignedMessage(message, CompressedPublicKey.of(keyPair.getPublic()));

            assertEquals(signedMessage1.hashCode(), signedMessage2.hashCode());
        }
    }
}