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
package org.drasyl.handler.remote;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

import static org.drasyl.handler.remote.LocalHostPeerInformation.deserializeAddress;
import static org.drasyl.handler.remote.LocalHostPeerInformation.serializeAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.aFileWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class LocalHostPeerInformationTest {
    @Nested
    class WriteTo {
        @Test
        void shouldWriteASerializedAddressPerLineToTheFile() throws IOException {
            Path tempDirectory = Files.createTempDirectory("test");
            final File file = tempDirectory.resolve("LocalHostPeerInformation.txt").toFile();

            Set<InetSocketAddress> addresses = Set.of(
                    new InetSocketAddress("127.0.0.1", 8080),
                    new InetSocketAddress("example.com", 6667),
                    new InetSocketAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348", 443)
            );
            new LocalHostPeerInformation(addresses).writeTo(file);

            assertThat(file, aFileWithSize(75));
        }
    }

    @Nested
    class Of {
        @Test
        void shouldCreateCorrectObjectFromPath() throws IOException {
            Path tempDirectory = Files.createTempDirectory("test");
            final Path file = tempDirectory.resolve("LocalHostPeerInformation.txt");
            Files.writeString(file, "[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443\n" +
                    "example.com:6667\n" +
                    "127.0.0.1:8080", StandardOpenOption.CREATE);

            assertEquals(Set.of(
                    new InetSocketAddress("127.0.0.1", 8080),
                    new InetSocketAddress("example.com", 6667),
                    new InetSocketAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348", 443)
            ), LocalHostPeerInformation.of(file).addresses());
        }
    }

    @Nested
    class SerializeAddress {
        @Test
        void shouldReturnSerializedAddress() {
            assertEquals("127.0.0.1:8080", serializeAddress(new InetSocketAddress("127.0.0.1", 8080)));
            assertEquals("example.com:6667", serializeAddress(new InetSocketAddress("example.com", 6667)));
            assertEquals("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443", serializeAddress(new InetSocketAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348", 443)));
        }
    }

    @Nested
    class DeserializeAddress {
        @Test
        void shouldReturnDeserializedAddress() {
            InetSocketAddress address = deserializeAddress("127.0.0.1");
            assertEquals("127.0.0.1", address.getAddress().getHostAddress());

            InetSocketAddress ip6 = deserializeAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348");
            assertEquals("2001:db8:85a3:8d3:1319:8a2e:370:7348", ip6.getAddress().getHostAddress());

            InetSocketAddress ip6port = deserializeAddress("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443");
            assertEquals("2001:db8:85a3:8d3:1319:8a2e:370:7348", ip6port.getAddress().getHostAddress());
            assertEquals(443, ip6port.getPort());

            final String host = "example.com";
            address = deserializeAddress(host);
            assertEquals(host, address.getHostName());

            final String hostAndPort = host + ":80";
            address = deserializeAddress(hostAndPort);
            assertEquals(host, address.getHostName());
            assertEquals(80, address.getPort());
        }
    }
}
