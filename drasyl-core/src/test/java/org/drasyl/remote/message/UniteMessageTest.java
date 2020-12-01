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

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UniteMessageTest {
    CompressedPublicKey sender;
    ProofOfWork proofOfWork;
    CompressedPublicKey recipient;
    private final int networkId = 1;
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
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + UniteMessage.class.getSimpleName() + "\",\"id\":\"1ezQfnyMzVauPAHi\",\"networkId\":1,\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"proofOfWork\":6657650,\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"publicKey\":\"Al6RczQotTXoEv2UsDcsS/LVJSC0U4kgms/UAxDOMF/0\",\"address\":\"127.0.0.1:12345\",\"userAgent\":\"\"}";

            assertEquals(new UniteMessage(
                    networkId,
                    CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"),
                    ProofOfWork.of(6657650),
                    CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"),
                    CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"),
                    new InetSocketAddress("127.0.0.1", 12345)
            ), JACKSON_READER.readValue(json, RemoteMessage.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + UniteMessage.class.getSimpleName() + "\",\"id\":\"1ezQfnyMzVauPAHi\",\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, RemoteMessage.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final UniteMessage message = new UniteMessage(networkId, sender, proofOfWork, recipient, publicKey, address);

            System.out.println(JACKSON_WRITER.writeValueAsString(message));

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", UniteMessage.class.getSimpleName())
                    .containsKeys("id", "networkId", "recipient", "hopCount", "sender", "publicKey", "address", "proofOfWork", "userAgent");
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