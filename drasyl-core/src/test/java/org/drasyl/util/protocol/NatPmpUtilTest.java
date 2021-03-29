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
import org.drasyl.util.protocol.NatPmpUtil.ExternalAddressResponseMessage;
import org.drasyl.util.protocol.NatPmpUtil.MappingUdpResponseMessage;
import org.drasyl.util.protocol.NatPmpUtil.Message;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static java.time.Duration.ofSeconds;
import static org.drasyl.util.protocol.NatPmpUtil.ResultCode.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class NatPmpUtilTest {
    @Nested
    class BuildExternalAddressRequestMessage {
        @Test
        void shouldReturnValidByteArray() {
            final byte[] message = NatPmpUtil.buildExternalAddressRequestMessage();

            assertEquals(2, message.length);
        }
    }

    @Nested
    class BuildMappingRequestMessage {
        @Test
        void shouldReturnValidByteArray() {
            final byte[] message = NatPmpUtil.buildMappingRequestMessage(12345, 23456, ofSeconds(600));

            assertEquals(12, message.length);
        }
    }

    @Nested
    class ReadMessage {
        @Test
        void shouldParseExternalAddressMessage() throws IOException {
            final byte[] bytes = HexUtil.fromString("008000000004f79fc0a8b202");

            final Message message = NatPmpUtil.readMessage(new ByteArrayInputStream(bytes));

            assertEquals(new ExternalAddressResponseMessage(SUCCESS, 325535, InetAddress.getByName("192.168.178.2")), message);
        }

        @Test
        void shouldParseMappingMessage() throws IOException {
            final byte[] bytes = HexUtil.fromString("008100000004f9bf63f163f100000258");

            final Message message = NatPmpUtil.readMessage(new ByteArrayInputStream(bytes));

            assertEquals(new MappingUdpResponseMessage(SUCCESS, 326079, 25585, 25585, 600), message);
        }
    }

    @Nested
    class TestMappingUdpResponseMessage {
        @Test
        void getterShouldReturnCorrectValues() {
            final MappingUdpResponseMessage response = new MappingUdpResponseMessage(SUCCESS, 326079, 25585, 25585, 600);

            assertEquals(SUCCESS, response.getResultCode());
            assertEquals(326079, response.getSecondsSinceStartOfEpoch());
            assertEquals(25585, response.getInternalPort());
            assertEquals(25585, response.getExternalPort());
            assertEquals(600, response.getLifetime());
        }
    }

    @Nested
    class TestExternalAddressResponseMessage {
        @Test
        void getterShouldReturnCorrectValues() throws UnknownHostException {
            final ExternalAddressResponseMessage response = new ExternalAddressResponseMessage(SUCCESS, 325535, InetAddress.getByName("192.168.178.2"));

            assertEquals(SUCCESS, response.getResultCode());
            assertEquals(325535, response.getSecondsSinceStartOfEpoch());
            assertEquals(InetAddress.getByName("192.168.178.2"), response.getExternalAddress());
        }
    }
}
