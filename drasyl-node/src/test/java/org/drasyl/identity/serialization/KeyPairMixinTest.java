/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.identity.serialization;

import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
import org.drasyl.identity.KeyPair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import test.util.IdentityTestUtil;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;

public class KeyPairMixinTest {
    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final KeyPair<IdentityPublicKey, IdentitySecretKey> keyPair = IdentityTestUtil.ID_1.getIdentityKeyPair();

            assertThatJson(JACKSON_WRITER.writeValueAsString(keyPair))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo("{\n" +
                            "  \"publicKey\": \"" + IdentityTestUtil.ID_1.getIdentityPublicKey() + "\",\n" +
                            "  \"secretKey\": \"" + IdentityTestUtil.ID_1.getIdentitySecretKey().toUnmaskedString() + "\"\n" +
                            "}");
        }
    }
}
