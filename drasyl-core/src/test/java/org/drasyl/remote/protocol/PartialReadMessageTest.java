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
package org.drasyl.remote.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.remote.handler.crypto.AgreementId;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static test.util.IdentityTestUtil.ID_1;
import static test.util.IdentityTestUtil.ID_2;

@ExtendWith(MockitoExtension.class)
public class PartialReadMessageTest {
    @Nested
    class Of {
        @Nested
        class WithPublicHeaderAndByteBuf {
            private ByteBuf bytes;

            @BeforeEach
            void setUp() {
                bytes = Unpooled.buffer();
            }

            @AfterEach
            void tearDown() {
                ReferenceCountUtil.safeRelease(bytes);
            }

            @Test
            void shouldReturnArmedMessageForNonChunks() {
                final PartialReadMessage message = PartialReadMessage.of(
                        PublicHeader.newBuilder()
                                .setNonce(Nonce.randomNonce().toByteString())
                                .setNetworkId(0)
                                .setSender(ID_1.getIdentityPublicKey().getBytes())
                                .setProofOfWork(ID_1.getProofOfWork().intValue())
                                .setRecipient(ID_2.getIdentityPublicKey().getBytes())
                                .setHopCount(1)
                                .setAgreementId(AgreementId.of(ID_1.getKeyAgreementPublicKey(), ID_2.getKeyAgreementPublicKey()).toByteString())
                                .build(),
                        bytes
                );

                assertThat(message, instanceOf(ArmedMessage.class));
            }
        }
    }
}
