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

import org.drasyl.identity.Identity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdentityMixinTest {
    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "{\"proofOfWork\":-2082598243,\"identityKeyPair\":{\"publicKey\":\"18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127\",\"secretKey\":\"65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127\"},\"keyAgreementKeyPair\":{\"publicKey\":\"0f2ad6d426694528942df15b8cb3a10140a5bfe28287c7eadfe5121a8badec53\",\"secretKey\":\"d06ed5fe2a4d645cd4770c0b9668a2fedc596ad90cf2cffcd947d26d8287ba7c\"}}";

            assertEquals(Identity.of(-2082598243,
                    "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
                    "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127"), JACKSON_READER.readValue(json, Identity.class));
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final Identity identity = Identity.of(-2082598243,
                    "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127",
                    "65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");

            assertThatJson(JACKSON_WRITER.writeValueAsString(identity))
                    .isEqualTo("{\"proofOfWork\":-2082598243,\"identityKeyPair\":{\"publicKey\":\"18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127\",\"secretKey\":\"65f20fc3fdcaf569cdcf043f79047723d8856b0169bd4c475ba15ef1b37d27ae18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127\"},\"keyAgreementKeyPair\":{\"publicKey\":\"0f2ad6d426694528942df15b8cb3a10140a5bfe28287c7eadfe5121a8badec53\",\"secretKey\":\"d06ed5fe2a4d645cd4770c0b9668a2fedc596ad90cf2cffcd947d26d8287ba7c\"}}");
        }
    }
}
