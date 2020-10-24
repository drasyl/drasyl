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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.HexUtil;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedKeyPair;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
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
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;

    @BeforeAll
    static void setUp() throws CryptoException {
        keyPair = CompressedKeyPair.of("0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c", "00b96ac2757f5f427a210c7a68f357bfa03f986b547a3b68e0bf79daa45f9edd").toUncompressedKeyPair();
    }

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"SignedMessage\",\"id\":\"89ba3cd9efb7570eb3126d11\",\"sender\":\"0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c\",\"proofOfWork\":6657650,\"recipient\":\"0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458\",\"signature\":{\"bytes\":\"eyJAdHlwZSI6IlBpbmdNZXNzYWdlIiwiaWQiOiI0YTRiNmEyYzRhZjA0NDllM2FkMGU5MmYiLCJzZW5kZXIiOiIwMzAwZjlkZjEyZWVkOTU3YTE3YjJiMzczOTc4ZWEzMjE3N2IzZTFjZTAwYzkyMDAzYjVkZDJjNjhkZTI1M2IzNWMiLCJwcm9vZk9mV29yayI6NjY1NzY1MH0=\"},\"payload\":{\"@type\":\"PingMessage\",\"id\":\"c36bee63dcf0c2998a8bedeb\",\"sender\":\"0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c\",\"proofOfWork\":6657650,\"recipient\":\"0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458\",\"userAgent\":\"\"},\"userAgent\":\"\"}";

            assertEquals(
                    new SignedMessage(
                            MessageId.of("89ba3cd9efb7570eb3126d11"),
                            "",
                            CompressedPublicKey.of("0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c"),
                            ProofOfWork.of(6657650),
                            CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"),
                            new Signature(
                                    HexUtil.fromString("7b224074797065223a2250696e674d657373616765222c226964223a22346134623661326334616630343439653361643065393266222c2273656e646572223a22303330306639646631326565643935376131376232623337333937386561333231373762336531636530306339323030336235646432633638646532353362333563222c2270726f6f664f66576f726b223a363635373635307d")
                            ),
                            new PingMessage(
                                    CompressedPublicKey.of("0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c"),
                                    ProofOfWork.of(6657650),
                                    CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458")
                            )
                    ),
                    JACKSON_READER.readValue(json, Message.class)
            );
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"SignedMessage\",\"sender\":\"0313c96bed7252c22218972dd21d611fec413d76e9eaac2717ed76889dcd357edf\",\"signature\":{\"bytes\":\"MEQCIAESpHbepeb9cRDA5Hd0GErCQnpSj+GN2bQIFEO2AgN5AiAGJwpL9G2BEki8c+VdCcnvKloYXKCDYJSTYt3e5VTylw==\"}}\n";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException, CryptoException {
            final SignedMessage message = new SignedMessage(CompressedPublicKey.of(keyPair.getPublic()), proofOfWork, recipient, new PingMessage(
                    CompressedPublicKey.of(keyPair.getPublic()),
                    ProofOfWork.of(6657650),
                    CompressedPublicKey.of("0300f9df12eed957a17b2b373978ea32177b3e1ce00c92003b5dd2c68de253b35c")
            ));
            Crypto.sign(keyPair.getPrivate(), message);

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", SignedMessage.class.getSimpleName())
                    .containsKeys("id", "payload", "sender", "proofOfWork", "recipient", "signature", "userAgent");
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() throws CryptoException {
            final PingMessage message = new PingMessage(sender, proofOfWork, recipient);
            final SignedMessage signedMessage1 = new SignedMessage(CompressedPublicKey.of(keyPair.getPublic()), proofOfWork, recipient, message);
            final SignedMessage signedMessage2 = new SignedMessage(CompressedPublicKey.of(keyPair.getPublic()), proofOfWork, recipient, message);

            assertEquals(signedMessage1, signedMessage2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() throws CryptoException {
            final PingMessage message = new PingMessage(sender, proofOfWork, recipient);
            final SignedMessage signedMessage1 = new SignedMessage(CompressedPublicKey.of(keyPair.getPublic()), proofOfWork, recipient, message);
            final SignedMessage signedMessage2 = new SignedMessage(CompressedPublicKey.of(keyPair.getPublic()), proofOfWork, recipient, message);

            assertEquals(signedMessage1.hashCode(), signedMessage2.hashCode());
        }
    }
}