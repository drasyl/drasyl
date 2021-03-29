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
package org.drasyl.util.protocol;

import org.drasyl.crypto.HexUtil;
import org.drasyl.util.protocol.PcpPortUtil.MappingResponseMessage;
import org.drasyl.util.protocol.PcpPortUtil.Message;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static java.time.Duration.ofSeconds;
import static org.drasyl.util.protocol.PcpPortUtil.PROTO_TCP;
import static org.drasyl.util.protocol.PcpPortUtil.PROTO_UDP;
import static org.drasyl.util.protocol.PcpPortUtil.ResultCode.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PcpPortUtilTest {
    @Nested
    class BuildMappingRequestMessage {
        @Test
        void shouldReturnValidByteArray() throws UnknownHostException {
            final byte[] message = PcpPortUtil.buildMappingRequestMessage(ofSeconds(30), InetAddress.getByName("192.168.188.3"), new byte[12], PROTO_TCP, 12345, InetAddress.getByName("83.98.12.19"));

            assertEquals(60, message.length);
        }
    }

    @Nested
    class ReadMessage {
        @Test
        void shouldParseMappingMessage() throws IOException {
            final byte[] bytes = HexUtil.fromString("02810000000002580004ea00000000000000000000000000027c2af0012b29445e68a77e1100000063f163f100000000000000000000ffffc0a8b202");

            final Message message = PcpPortUtil.readMessage(new ByteArrayInputStream(bytes));

            assertEquals(new MappingResponseMessage(
                    SUCCESS,
                    600,
                    322048,
                    new byte[]{ 2, 124, 42, -16, 1, 43, 41, 68, 94, 104, -89, 126 },
                    PROTO_UDP,
                    25585,
                    25585,
                    InetAddress.getByName("192.168.178.2")
            ), message);
        }
    }

    @Nested
    class TestMappingResponseMessage {
        @Test
        void getterShouldReturnCorrectValues() throws UnknownHostException {
            final MappingResponseMessage message = new MappingResponseMessage(
                    SUCCESS,
                    600,
                    322048,
                    new byte[]{ 2, 124, 42, -16, 1, 43, 41, 68, 94, 104, -89, 126 },
                    PROTO_UDP,
                    25585,
                    25585,
                    InetAddress.getByName("192.168.178.2")
            );

            assertEquals(SUCCESS, message.getResultCode());
            assertEquals(600, message.getLifetime());
            assertEquals(322048, message.getEpochTime());
            assertArrayEquals(new byte[]{
                    2,
                    124,
                    42,
                    -16,
                    1,
                    43,
                    41,
                    68,
                    94,
                    104,
                    -89,
                    126
            }, message.getMappingNonce());
            assertEquals(PROTO_UDP, message.getProtocol());
            assertEquals(25585, message.getInternalPort());
            assertEquals(25585, message.getExternalSuggestedPort());
            assertEquals(InetAddress.getByName("192.168.178.2"), message.getExternalSuggestedAddress());
        }
    }
}
