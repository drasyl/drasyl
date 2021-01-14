/*
 * Copyright (c) 2021.
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

            assertEquals(new ExternalAddressResponseMessage(0, 325535, InetAddress.getByName("192.168.178.2")), message);
        }

        @Test
        void shouldParseMappingMessage() throws IOException {
            final byte[] bytes = HexUtil.fromString("008100000004f9bf63f163f100000258");

            final Message message = NatPmpUtil.readMessage(new ByteArrayInputStream(bytes));

            assertEquals(new MappingUdpResponseMessage(0, 326079, 25585, 25585, 600), message);
        }
    }

    @Nested
    class TestMappingUdpResponseMessage {
        @Test
        void getterShouldReturnCorrectValues() {
            final MappingUdpResponseMessage response = new MappingUdpResponseMessage(0, 326079, 25585, 25585, 600);

            assertEquals(0, response.getResultCode());
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
            final ExternalAddressResponseMessage response = new ExternalAddressResponseMessage(0, 325535, InetAddress.getByName("192.168.178.2"));

            assertEquals(0, response.getResultCode());
            assertEquals(325535, response.getSecondsSinceStartOfEpoch());
            assertEquals(InetAddress.getByName("192.168.178.2"), response.getExternalAddress());
        }
    }
}
