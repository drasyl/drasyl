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
package org.drasyl.util.network;

import org.drasyl.util.ThrowingFunction;
import org.drasyl.util.ThrowingSupplier;
import org.drasyl.util.network.NetworkUtil.NetworkUtilImpl;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.function.Supplier;

import static org.drasyl.util.network.NetworkUtil.createInetAddress;
import static org.drasyl.util.network.NetworkUtil.getAddresses;
import static org.drasyl.util.network.NetworkUtil.getNetworkPrefixLength;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkUtilTest {
    @Mock
    private ThrowingSupplier<InputStream, IOException> defaultGatewayProvider;
    @Mock
    private ThrowingFunction<URL, URLConnection, IOException> urlConnectionProvider;
    @Mock
    private Supplier<Socket> socketSupplier;

    @Nested
    class GetExternalIPv4Address {
        @Test
        void shouldReturnIPv4AddressReportedByProvider(@Mock(answer = RETURNS_DEEP_STUBS) final URLConnection urlConnection) throws IOException {
            final URL provider = new URL("https://4.example.com");
            when(urlConnectionProvider.apply(any())).thenReturn(urlConnection);
            when(urlConnection.getInputStream()).thenReturn(new ByteArrayInputStream("84.81.69.200".getBytes()));

            final NetworkUtilImpl util = new NetworkUtilImpl(defaultGatewayProvider, urlConnectionProvider, socketSupplier);
            final InetAddress address = util.getExternalIPAddress(new URL[]{ provider });

            assertEquals(InetAddress.getByName("84.81.69.200"), address);
        }

        @Test
        void shouldReturnIPv6AddressReportedByProvider(@Mock(answer = RETURNS_DEEP_STUBS) final URLConnection urlConnection) throws IOException {
            final URL provider = new URL("https://6.example.com");
            when(urlConnectionProvider.apply(any())).thenReturn(urlConnection);
            when(urlConnection.getInputStream()).thenReturn(new ByteArrayInputStream("bb95:2626:bbdc:dbac:c81:1018:347e:7f9c".getBytes()));

            final NetworkUtilImpl util = new NetworkUtilImpl(defaultGatewayProvider, urlConnectionProvider, socketSupplier);
            final InetAddress address = util.getExternalIPAddress(new URL[]{ provider });

            assertEquals(InetAddress.getByName("bb95:2626:bbdc:dbac:c81:1018:347e:7f9c"), address);
        }
    }

    @SuppressWarnings({ "CatchMayIgnoreException", "unused" })
    @Nested
    class Available {
        @Test
        void shouldReturnCorrectValue() {
            try (final ServerSocket socket = new ServerSocket(5555)) {
                assertFalse(NetworkUtil.available(5555));
            }
            catch (final IOException e) {
            }

            assertTrue(NetworkUtil.available(4444));
        }

        @Test
        void shouldRejectInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.available(NetworkUtil.MIN_PORT_NUMBER - 1));
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.available(NetworkUtil.MAX_PORT_NUMBER + 1));
        }
    }

    @SuppressWarnings({ "CatchMayIgnoreException", "unused" })
    @Nested
    class Alive {
        @Test
        void shouldReturnCorrectValue() {
            try (final ServerSocket socket = new ServerSocket(2222)) {
                assertTrue(NetworkUtil.alive("127.0.0.1", 2222));
            }
            catch (final IOException e) {
            }

            assertFalse(NetworkUtil.alive("127.0.0.1", 3333));
        }

        @Test
        void shouldRejectInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.alive("127.0.0.1", NetworkUtil.MIN_PORT_NUMBER - 1));
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.alive("127.0.0.1", NetworkUtil.MAX_PORT_NUMBER + 1));
        }
    }

    @Nested
    class GetAddresses {
        @Test
        void shouldReturnNotNull() {
            assertNotNull(getAddresses());
        }
    }

    @Nested
    class GetLocalHostName {
        @Test
        void shouldReturnHostName() throws UnknownHostException {
            assertEquals(
                    InetAddress.getLocalHost().getHostName(),
                    NetworkUtil.getLocalHostName()
            );
        }
    }

    @Nested
    class CreateInetAddress {
        @Test
        void shouldReturnInetAddressForValidString() throws UnknownHostException {
            assertEquals(InetAddress.getByName("192.168.188.112"), createInetAddress("192.168.188.112"));
        }

        @Test
        void shouldThrowIllegalArgumentExceptionForInvalidString() {
            assertThrows(IllegalArgumentException.class, () -> createInetAddress("foo.bar"));
        }
    }

    @Nested
    class GetNetworkPrefixLength {
        @Test
        void shouldNotThrowException() {
            for (final InetAddress address : getAddresses()) {
                assertDoesNotThrow(() -> getNetworkPrefixLength(address));
            }
        }
    }

    @Nested
    class Cidr2Netmask {
        @Test
        void shouldConvertToCorrectNetmaskForIPv4() throws UnknownHostException {
            final InetAddress mask = InetAddress.getByName("255.255.255.0");
            final short cidr = 24;

            assertArrayEquals(mask.getAddress(), NetworkUtil.cidr2Netmask(cidr, 4));
        }

        @Test
        void shouldConvertToCorrectNetmaskForIPv6() throws UnknownHostException {
            final InetAddress mask = InetAddress.getByName("ffff:ffff:ffff:ff80::");
            final short cidr = 57;

            assertArrayEquals(mask.getAddress(), NetworkUtil.cidr2Netmask(cidr, 16));
        }

        @Test
        void shouldThrowExceptionOnInvalidInput() {
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.cidr2Netmask((short) 24, 3));
            assertThrows(IllegalArgumentException.class, () -> NetworkUtil.cidr2Netmask((short) 24, 17));
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nested
    class SameNetwork {
        @Test
        void shouldWorkForSameNetworkIPv4() throws UnknownHostException {
            final InetAddress currentIP = InetAddress.getByName("192.168.1.129");
            final InetAddress firstIP = InetAddress.getByName("192.168.1.0");
            final InetAddress lastIP = InetAddress.getByName("192.168.1.255");
            final InetAddress lastNotIncludedIP = InetAddress.getByName("192.168.0.255");
            final InetAddress firstNotIncludedIP = InetAddress.getByName("192.168.2.0");
            final short netmask = (short) 24;

            final byte[] currentIPBytes = currentIP.getAddress();

            assertTrue(NetworkUtil.sameNetwork(firstIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(lastIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIPBytes, currentIPBytes, netmask));
            assertFalse(NetworkUtil.sameNetwork(lastNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(firstNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(currentIPBytes, new byte[]{}, netmask));
        }

        @Test
        void shouldReturnFalseOnNull() {
            assertFalse(NetworkUtil.sameNetwork(new byte[]{}, null, (short) 24));
            assertFalse(NetworkUtil.sameNetwork(null, new byte[]{}, (short) 24));
        }

        @Test
        void shouldThrowExceptionOnNull() throws UnknownHostException {
            final InetAddress address = InetAddress.getByName("192.168.1.129");

            assertThrows(NullPointerException.class, () -> NetworkUtil.sameNetwork(address, null, (short) 24));
            assertThrows(NullPointerException.class, () -> NetworkUtil.sameNetwork(null, address, (short) 24));
        }

        @Test
        void shouldWorkForSameNetworkIPv6() throws UnknownHostException {
            final InetAddress currentIP = InetAddress.getByName("2001:0db8:85a3:08d3:1319:8a2e:0370:7347");
            final InetAddress firstIP = InetAddress.getByName("2001:0db8:85a3:0880:0000:0000:0000:0000");
            final InetAddress lastIP = InetAddress.getByName("2001:0db8:85a3:08ff:ffff:ffff:ffff:ffff");
            final InetAddress lastNotIncludedIP = InetAddress.getByName("2001:0db8:85a3:07ff:ffff:ffff:ffff:ffff");
            final InetAddress firstNotIncludedIP = InetAddress.getByName("2001:0db8:85a3:0980:0000:0000:0000:0000");
            final short netmask = (short) 57;

            final byte[] currentIPBytes = currentIP.getAddress();

            assertTrue(NetworkUtil.sameNetwork(firstIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(lastIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIP, currentIP, netmask));
            assertTrue(NetworkUtil.sameNetwork(currentIPBytes, currentIPBytes, netmask));
            assertFalse(NetworkUtil.sameNetwork(lastNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(firstNotIncludedIP, currentIP, netmask));
            assertFalse(NetworkUtil.sameNetwork(currentIPBytes, new byte[]{}, netmask));
        }
    }

    @Nested
    class GetDefaultGateway {
        static final String NETSTAT_MACOS = "Routing tables\n" +
                "\n" +
                "Internet:\n" +
                "Destination        Gateway            Flags        Netif Expire\n" +
                "default            192.168.188.1      UGSc           en0";
        static final String NETSTAT_WIN = "Aktive Routen:\n" +
                "     Netzwerkziel    Netzwerkmaske          Gateway    Schnittstelle Metrik\n" +
                "          0.0.0.0          0.0.0.0         10.0.2.2        10.0.2.15     25";

        @Test
        void shouldParseCorrectAddressOnMacOS(@Mock final ThrowingSupplier<InputStream, IOException> defaultGatewayProvider) throws Exception {
            when(defaultGatewayProvider.get()).thenReturn(new ByteArrayInputStream(NETSTAT_MACOS.getBytes()));

            final NetworkUtilImpl util = new NetworkUtilImpl(defaultGatewayProvider, urlConnectionProvider, socketSupplier);

            assertEquals(Inet4Address.getByName("192.168.188.1"), util.getDefaultGateway());
        }

        @Test
        void shouldParseCorrectAddressOnWindows(@Mock final ThrowingSupplier<InputStream, IOException> defaultGatewayProvider) throws Exception {
            when(defaultGatewayProvider.get()).thenReturn(new ByteArrayInputStream(NETSTAT_WIN.getBytes()));

            final NetworkUtilImpl util = new NetworkUtilImpl(defaultGatewayProvider, urlConnectionProvider, socketSupplier);

            assertEquals(Inet4Address.getByName("10.0.2.2"), util.getDefaultGateway());
        }
    }

    @Nested
    class GetIpv4MappedIPv6AddressBytes {
        @Test
        void shouldReturnUnchangedInet6Address() throws UnknownHostException {
            final InetAddress address = InetAddress.getByName("2001:0db8:85a3:08d3:1319:8a2e:0370:7347");

            assertArrayEquals(new byte[]{
                    32,
                    1,
                    13,
                    -72,
                    -123,
                    -93,
                    8,
                    -45,
                    19,
                    25,
                    -118,
                    46,
                    3,
                    112,
                    115,
                    71
            }, NetworkUtil.getIpv4MappedIPv6AddressBytes(address));
        }

        @Test
        void shouldReturnMappedInet6Address() throws UnknownHostException {
            final InetAddress address = InetAddress.getByName("192.168.1.129");

            assertArrayEquals(new byte[]{
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    -1,
                    -1,
                    -64,
                    -88,
                    1,
                    -127
            }, NetworkUtil.getIpv4MappedIPv6AddressBytes(address));
        }
    }

    @Nested
    class GetLocalAddressForRemoteAddress {
        @Test
        void shouldReturnLocalAddress(@Mock(answer = RETURNS_DEEP_STUBS) final Socket socket,
                                      @Mock final InetSocketAddress remoteAddress) {
            when(socketSupplier.get()).thenReturn(socket);

            final NetworkUtilImpl util = new NetworkUtilImpl(defaultGatewayProvider, urlConnectionProvider, socketSupplier);
            final InetAddress address = util.getLocalAddressForRemoteAddress(remoteAddress);

            assertEquals(socket.getLocalAddress(), address);
        }

        @Test
        void shouldReturnNullOnException(@Mock(answer = RETURNS_DEEP_STUBS) final Socket socket,
                                         @Mock final InetSocketAddress remoteAddress) throws IOException {
            when(socketSupplier.get()).thenReturn(socket);
            doThrow(IOException.class).when(socket).connect(any(), anyInt());

            final NetworkUtilImpl util = new NetworkUtilImpl(defaultGatewayProvider, urlConnectionProvider, socketSupplier);
            final InetAddress address = util.getLocalAddressForRemoteAddress(remoteAddress);

            assertNull(address);
        }
    }

    @Nested
    class GetDefaultInterface {
        @Test
        void shouldNeverThrowException() {
            assertDoesNotThrow(NetworkUtil::getDefaultInterface);
        }
    }
}
