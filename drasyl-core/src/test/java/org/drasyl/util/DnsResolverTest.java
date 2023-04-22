/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.util;

import org.drasyl.util.DnsResolver.DnsResolverImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static io.netty.resolver.ResolvedAddressTypes.IPV4_ONLY;
import static io.netty.resolver.ResolvedAddressTypes.IPV4_PREFERRED;
import static io.netty.resolver.ResolvedAddressTypes.IPV6_ONLY;
import static io.netty.resolver.ResolvedAddressTypes.IPV6_PREFERRED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DnsResolverTest {
    @Mock
    private ThrowingFunction<String, InetAddress[], UnknownHostException> allByNameProvider;
    private Inet4Address inet4Address;
    private Inet6Address inet6Address;

    @BeforeEach
    void setUp() throws UnknownHostException {
        inet4Address = (Inet4Address) Inet4Address.getByName("10.10.10.10");
        inet6Address = (Inet6Address) Inet4Address.getByName("fd12:3456:789a:1::1");
    }

    @Test
    @Disabled("test depends on external services, which may not be always given")
    void resolveAll() throws UnknownHostException {
        final InetAddress[] addresses = DnsResolver.resolveAll("ipv6.ipecho.roebert.eu");
        System.out.println(Arrays.toString(addresses));
    }

    @Nested
    class ResolveAll {
        @Test
        void shouldThrowExceptionIfHostIsUnknown() throws UnknownHostException {
            when(allByNameProvider.apply(any())).thenReturn(new InetAddress[0]);

            final DnsResolverImpl resolver = new DnsResolverImpl(IPV4_ONLY, allByNameProvider);
            assertThrows(UnknownHostException.class, () -> resolver.resolveAll("example.com"));
        }

        @Nested
        class OnAnIpv4OnlySystem {
            @Test
            void shouldOnlyReturnIpv4Addresses() throws UnknownHostException {
                when(allByNameProvider.apply(any())).thenReturn(new InetAddress[]{
                        inet4Address,
                        inet6Address
                });

                final DnsResolverImpl resolver = new DnsResolverImpl(IPV4_ONLY, allByNameProvider);
                assertArrayEquals(new InetAddress[]{
                        inet4Address
                }, resolver.resolveAll("example.com"));
            }
        }

        @Nested
        class OnAnIpv6OnlySystem {
            @Test
            void shouldOnlyReturnIpv6Addresses() throws UnknownHostException {
                when(allByNameProvider.apply(any())).thenReturn(new InetAddress[]{
                        inet4Address,
                        inet6Address
                });

                final DnsResolverImpl resolver = new DnsResolverImpl(IPV6_ONLY, allByNameProvider);
                assertArrayEquals(new InetAddress[]{
                        inet6Address
                }, resolver.resolveAll("example.com"));
            }
        }

        @Nested
        class OnAnIpv4PreferredSystem {
            @Test
            void shouldReturnIpv4AddressesFirst() throws UnknownHostException {
                when(allByNameProvider.apply(any())).thenReturn(new InetAddress[]{
                        inet6Address,
                        inet4Address
                });

                final DnsResolverImpl resolver = new DnsResolverImpl(IPV4_PREFERRED, allByNameProvider);
                assertArrayEquals(new InetAddress[]{
                        inet4Address,
                        inet6Address
                }, resolver.resolveAll("example.com"));
            }
        }

        @Nested
        class OnAnIpv6PreferredSystem {
            @Test
            void shouldReturnIpv6AddressesFirst() throws UnknownHostException {
                when(allByNameProvider.apply(any())).thenReturn(new InetAddress[]{
                        inet4Address,
                        inet6Address
                });

                final DnsResolverImpl resolver = new DnsResolverImpl(IPV6_PREFERRED, allByNameProvider);
                assertArrayEquals(new InetAddress[]{
                        inet6Address,
                        inet4Address
                }, resolver.resolveAll("example.com"));
            }
        }
    }
}
