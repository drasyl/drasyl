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
class DiscoverMessageTest {
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
            final String json = "{\"@type\":\"" + DiscoverMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"userAgent\":\"\",\"proofOfWork\":3556154,\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"networkId\":1337,\"joinTime\":0}";

            assertEquals(new DiscoverMessage(
                    1337,
                    CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb"),
                    ProofOfWork.of(3556154),
                    CompressedPublicKey.of("033de3da699f6f9ffbd427c56725910655ba3913be4ff55b13c628e957c860fd55"),
                    0
            ), JACKSON_READER.readValue(json, RemoteMessage.class));
        }

        @Test
        void shouldRejectIncompleteData() {
            final String json = "{\"@type\":\"" + DiscoverMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\"}";

            assertThrows(ValueInstantiationException.class, () -> JACKSON_READER.readValue(json, RemoteMessage.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final DiscoverMessage message = new DiscoverMessage(1337, sender, ProofOfWork.of(1), recipient, System.currentTimeMillis());

            assertThatJson(JACKSON_WRITER.writeValueAsString(message))
                    .isObject()
                    .containsEntry("@type", DiscoverMessage.class.getSimpleName())
                    .containsKeys("id", "userAgent", "proofOfWork", "sender", "recipient", "joinTime", "networkId", "hopCount");
        }
    }

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final long joinTime = System.currentTimeMillis();
            assertThrows(NullPointerException.class, () -> new DiscoverMessage(1337, null, proofOfWork, recipient, joinTime), "Join requires a public key");
            assertThrows(NullPointerException.class, () -> new DiscoverMessage(1337, null, null, recipient, joinTime), "Join requires a public key and endpoints");
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final DiscoverMessage message1 = new DiscoverMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final DiscoverMessage message2 = new DiscoverMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final DiscoverMessage message3 = new DiscoverMessage(1337, sender2, proofOfWork, recipient, childrenJoin);

            assertEquals(message1, message2);
            assertNotEquals(message2, message3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfDifferentPublicKey() {
            final DiscoverMessage message1 = new DiscoverMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final DiscoverMessage message2 = new DiscoverMessage(1337, sender, proofOfWork, recipient, childrenJoin);
            final DiscoverMessage message3 = new DiscoverMessage(1337, sender2, proofOfWork, recipient, childrenJoin);

            assertEquals(message1.hashCode(), message2.hashCode());
            assertNotEquals(message2.hashCode(), message3.hashCode());
        }
    }
}