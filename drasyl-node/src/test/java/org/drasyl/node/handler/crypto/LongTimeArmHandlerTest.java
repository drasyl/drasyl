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
package org.drasyl.node.handler.crypto;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.sodium.SessionPair;
import org.drasyl.handler.remote.protocol.Nonce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LongTimeArmHandlerTest {
    private Duration sessionExpireTime;
    private int maxAgreements;

    @BeforeEach
    void setUp() {
        sessionExpireTime = Duration.ofSeconds(100000);
        maxAgreements = 2;
    }

    @Nested
    class Encryption {
        @Test
        void shouldEncryptOutboundMessage() throws CryptoException {
            final LongTimeArmHandler handler = new LongTimeArmHandler(Crypto.INSTANCE, sessionExpireTime, maxAgreements, IdentityTestUtil.ID_1, IdentityTestUtil.ID_2.getIdentityPublicKey());
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ByteBuf msg = Unpooled.buffer();

                channel.writeAndFlush(msg);

                assertThat(channel.readOutbound(), instanceOf(ArmHeader.class));
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Decryption {
        @Test
        void shouldDecryptInboundMessage(@Mock final Crypto crypto,
                                         @Mock final Session session,
                                         @Mock final Agreement agreement,
                                         @Mock final AgreementId agreementId,
                                         @Mock final Nonce nonce,
                                         @Mock final SessionPair sessionPair) throws CryptoException {
            final ByteBuf byteBuf = Unpooled.buffer()
                    .writeByte(ArmMessage.MessageType.APPLICATION.getByte());

            when(session.getLongTimeAgreement()).thenReturn(agreement);
            when(agreement.getSessionPair()).thenReturn(sessionPair);
            when(crypto.decrypt(any(), any(), any(), any())).thenReturn(ByteBufUtil.getBytes(byteBuf));

            final LongTimeArmHandler handler = new LongTimeArmHandler(crypto, IdentityTestUtil.ID_1, IdentityTestUtil.ID_2.getIdentityPublicKey(), session);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                final ArmHeader msg = ArmHeader.of(agreementId, nonce, byteBuf);

                channel.writeInbound(msg);

                assertThat(channel.readInbound(), instanceOf(ByteBuf.class));
            }
            finally {
                channel.close();
            }
        }
    }
}
