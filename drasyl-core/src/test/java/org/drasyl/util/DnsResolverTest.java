package org.drasyl.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

class DnsResolverTest {
    @Test
    @Disabled("test depends on external services, which may not be always given")
    void resolveAll() throws UnknownHostException {
        final InetAddress[] addresses = DnsResolver.resolveAll("ipv6.ipecho.roebert.eu");
        System.out.println(Arrays.toString(addresses));
    }
}
