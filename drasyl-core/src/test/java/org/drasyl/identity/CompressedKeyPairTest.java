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
package org.drasyl.identity;

import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressedKeyPairTest {
    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "{\n" +
                    "  \"publicKey\": \"AnduU7DI6cLHCLZ0x5KebE5EXE+XpAdwAstnnE3QhXYJ\",\n" +
                    "  \"privateKey\": \"B5Cf44xUUxCYBd5AwkqRpfPi3kjxVLddhpSSfzyATzY=\"\n" +
                    "}";

            assertEquals(
                    CompressedKeyPair.of("02776e53b0c8e9c2c708b674c7929e6c4e445c4f97a4077002cb679c4dd0857609", "07909fe38c5453109805de40c24a91a5f3e2de48f154b75d8694927f3c804f36"),
                    JACKSON_READER.readValue(json, CompressedKeyPair.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final CompressedKeyPair keyPair = CompressedKeyPair.of("02776e53b0c8e9c2c708b674c7929e6c4e445c4f97a4077002cb679c4dd0857609", "07909fe38c5453109805de40c24a91a5f3e2de48f154b75d8694927f3c804f36");

            assertThatJson(JACKSON_WRITER.writeValueAsString(keyPair))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo("{\n" +
                            "  \"publicKey\": \"AnduU7DI6cLHCLZ0x5KebE5EXE+XpAdwAstnnE3QhXYJ\",\n" +
                            "  \"privateKey\": \"B5Cf44xUUxCYBd5AwkqRpfPi3kjxVLddhpSSfzyATzY=\"\n" +
                            "}");
        }
    }
}
