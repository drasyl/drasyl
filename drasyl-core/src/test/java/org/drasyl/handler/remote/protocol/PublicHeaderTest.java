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
package org.drasyl.handler.remote.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
class PublicHeaderTest {
    @Nested
    static class Serialization {
        @ParameterizedTest
        @MethodSource("shouldSerializeCorrectlyAndViceVersaProvider")
        void shouldSerializeCorrectlyAndViceVersa(final PublicHeader publicHeader) throws InvalidMessageFormatException {
            final ByteBuf out = Unpooled.buffer();
            publicHeader.writeTo(out);

            assertEquals(PublicHeader.LENGTH, out.readableBytes());

            final PublicHeader deserializedHeader = PublicHeader.of(out);
            assertEquals(publicHeader, deserializedHeader);

            out.release();
        }

        static Collection<PublicHeader> shouldSerializeCorrectlyAndViceVersaProvider() {
            return Arrays.asList(new PublicHeader[]{
                    PublicHeader.of(HopCount.of(0), false, 0, Nonce.randomNonce(), ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork()),
                    PublicHeader.of(HopCount.of(1), false, 0, Nonce.randomNonce(), ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork()),
                    PublicHeader.of(HopCount.of(0), true, 0, Nonce.randomNonce(), ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork()),
                    PublicHeader.of(HopCount.of(1), true, 0, Nonce.randomNonce(), ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork())
            });
        }
    }

    @Nested
    class WriteTo {
        @Test
        void shouldSerializeToCorrectLenght() {
            final ByteBuf out = Unpooled.buffer();
            final PublicHeader publicHeader = PublicHeader.of(HopCount.of(), false, 0, Nonce.randomNonce(), ID_1.getIdentityPublicKey(), ID_2.getIdentityPublicKey(), ID_2.getProofOfWork());
            publicHeader.writeTo(out);

            assertEquals(PublicHeader.LENGTH, out.readableBytes());
            out.release();
        }
    }
}
