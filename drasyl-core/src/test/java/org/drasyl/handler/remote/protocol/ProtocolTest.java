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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.drasyl.identity.ProofOfWork;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import test.util.IdentityTestUtil;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProtocolTest {
    @Nested
    class TestPublicHeader {
        @Test
        void shouldSerializeToCorrectSize() throws InvalidMessageFormatException {
            final PublicHeader header = PublicHeader.of(
                    HopCount.of(1),
                    true,
                    Integer.MIN_VALUE,
                    Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7"),
                    IdentityTestUtil.ID_2.getIdentityPublicKey(),
                    IdentityTestUtil.ID_1.getIdentityPublicKey(),
                    ProofOfWork.of(Integer.MAX_VALUE)
            );

            final ByteBuf byteBuf = Unpooled.buffer();
            header.writeTo(byteBuf);

            assertEquals(PublicHeader.LENGTH, ByteBufUtil.getBytes(byteBuf).length);
            assertNotNull(PublicHeader.of(byteBuf).getRecipient());

            byteBuf.release();
        }

        @Test
        void shouldSerializeEmptyRecipient() throws InvalidMessageFormatException {
            final PublicHeader header = PublicHeader.of(
                    HopCount.of(1),
                    true,
                    Integer.MIN_VALUE,
                    Nonce.of("ea0f284eef1567c505b126671f4293924b81b4b9d20a2be7"),
                    null,
                    IdentityTestUtil.ID_1.getIdentityPublicKey(),
                    ProofOfWork.of(Integer.MAX_VALUE)
            );

            final ByteBuf byteBuf = Unpooled.buffer();
            header.writeTo(byteBuf);

            assertEquals(PublicHeader.LENGTH, ByteBufUtil.getBytes(byteBuf).length);
            assertNull(PublicHeader.of(byteBuf).getRecipient());

            byteBuf.release();
        }
    }

    @Nested
    class TestUnite {
        @Test
        void shouldSerializeIpv4ToCorrectSize() {
            final InetSocketAddress address = new InetSocketAddress("37.61.174.58", 80);
            final UniteMessage unit = UniteMessage.of(
                    -1,
                    IdentityTestUtil.ID_2.getIdentityPublicKey(),
                    IdentityTestUtil.ID_1.getIdentityPublicKey(),
                    IdentityTestUtil.ID_1.getProofOfWork(),
                    IdentityTestUtil.ID_1.getIdentityPublicKey(),
                    address);
            final ByteBuf byteBuf = Unpooled.buffer();
            unit.writeBodyTo(byteBuf);

            assertEquals(UniteMessage.LENGTH, byteBuf.readableBytes());
        }

        @Test
        void shouldSerializeIpv6ToCorrectSize() {
            final InetSocketAddress address = new InetSocketAddress("b719:5781:d127:d17c:1230:b24c:478c:7985", 443);
            final UniteMessage unit = UniteMessage.of(
                    -1,
                    IdentityTestUtil.ID_2.getIdentityPublicKey(),
                    IdentityTestUtil.ID_1.getIdentityPublicKey(),
                    IdentityTestUtil.ID_1.getProofOfWork(),
                    IdentityTestUtil.ID_1.getIdentityPublicKey(),
                    address);
            final ByteBuf byteBuf = Unpooled.buffer();
            unit.writeBodyTo(byteBuf);

            assertEquals(UniteMessage.LENGTH, byteBuf.readableBytes());
        }
    }
}
