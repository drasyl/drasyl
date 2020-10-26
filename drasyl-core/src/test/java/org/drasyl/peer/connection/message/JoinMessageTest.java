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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class JoinMessageTest {
    @Mock
    private CompressedPublicKey sender;
    private final long childrenJoin = 0;
    @Mock
    private CompressedPublicKey sender2;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private ProofOfWork proofOfWork;

    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException, CryptoException {
            final String json = "{\"@type\":\"" + JoinMessage.class.getSimpleName() + "\",\"id\":\"4ae5cdcd8c21719f8e779f21\",\"userAgent\":\"\",\"proofOfWork\":3556154,\"sender\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\",\"recipient\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\",\"networkId\":1337,\"joinTime\":0}";

            assertEquals(new JoinMessage(
                    1337,
                    CompressedPublicKey.of("034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d"),
                    ProofOfWork.of(3556154),
                    CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9"),
                    0
            ), JACKSON_READER.readValue(json, Message.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + JoinMessage.class.getSimpleName() + "\",\"id\":\"4ae5cdcd8c21719f8e779f21\",\"sender\":\"034a450eb7955afb2f6538433ae37bd0cbc09745cf9df4c7ccff80f8294e6b730d\",\"recipient\":\"0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, Message.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final JoinMessage message = new JoinMessage(1337, sender, ProofOfWork.of(1), recipient, System.currentTimeMillis());

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", JoinMessage.class.getSimpleName())
                    .containsKeys("id", "userAgent", "proofOfWork", "sender", "recipient", "joinTime", "networkId", "hopCount");
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final long joinTime = System.currentTimeMillis();
            assertThrows(NullPointerException.class, () -> new JoinMessage(1337, null, proofOfWork, recipient, joinTime), "Join requires a public key");
            assertThrows(NullPointerException.class, () -> new JoinMessage(1337, null, null, recipient, joinTime), "Join requires a public key and endpoints");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final JoinMessage message1 = new JoinMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final JoinMessage message2 = new JoinMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final JoinMessage message3 = new JoinMessage(1337, sender2, proofOfWork, recipient, childrenJoin);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final JoinMessage message1 = new JoinMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final JoinMessage message2 = new JoinMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final JoinMessage message3 = new JoinMessage(1337, sender2, proofOfWork, recipient, childrenJoin);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}