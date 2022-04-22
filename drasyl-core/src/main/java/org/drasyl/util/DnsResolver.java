package org.drasyl.util;

import io.netty.resolver.ResolvedAddressTypes;
import io.netty.util.NetUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static io.netty.resolver.ResolvedAddressTypes.IPV4_ONLY;
import static io.netty.resolver.ResolvedAddressTypes.IPV4_PREFERRED;
import static io.netty.resolver.ResolvedAddressTypes.IPV6_PREFERRED;

/**
 * Helper class for resolving hostnames to IP addresses.
 */
public final class DnsResolver {
    private static final Logger LOG = LoggerFactory.getLogger(DnsResolver.class);
    static final ResolvedAddressTypes DEFAULT_RESOLVE_ADDRESS_TYPES;

    private DnsResolver() {
        // util class
    }

    static {
        if (NetUtil.isIpV4StackPreferred() || !anyInterfaceSupportsIpV6()) {
            DEFAULT_RESOLVE_ADDRESS_TYPES = IPV4_ONLY;
        }
        else {
            if (NetUtil.isIpV6AddressesPreferred()) {
                DEFAULT_RESOLVE_ADDRESS_TYPES = IPV6_PREFERRED;
            }
            else {
                DEFAULT_RESOLVE_ADDRESS_TYPES = IPV4_PREFERRED;
            }
        }
    }

    /**
     * Given the name of a host, returns an array of its IP addresses, based on the configured name
     * service on the system.
     * <p>
     * In contrast to {@link InetAddress#getAllByName(String)}, this method takes care if resolved
     * addresses are theoretically reachable from the local host. With the native JDK (especially
     * OpenJDK) implementation we could observe that on IPv4-only systems hostnames are sometimes
     * resolved to IPv6 addresses. This behavior is not deterministic and therefore makes the use of
     * JDK-based resolution a gamble.
     * <p>
     * When the program is started, it checks whether there is at least one IPv6-enabled network
     * interface. If not, only resolved IPv4 addresses are returned by this method. It also takes
     * into account which Internet Protocol version is preferred by Java. Addresses of the preferred
     * version are located at the beginning of the returned list. If no version is preferred, IPv4
     * addresses are listed first.
     *
     * @param host name of host to resolve
     * @throws UnknownHostException if no IP address for the {@code host} could be found, or if a
     *                              scope_id was specified for a global IPv6 address.
     * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html">Java
     * SE networking properties</a>
     * @see <a href="https://blog.bmarwell.de/2020/09/23/javas-dns-resolution-is-so-90ies.html">Java’s
     * DNS resolution is so 90ies!</a>
     */
    @SuppressWarnings("java:S3776")
    public static InetAddress[] resolveAll(final String host) throws UnknownHostException {
        // use JDK-based resolution to get all candidates
        final InetAddress[] addresses = InetAddress.getAllByName(host);

        // now group candidates by IP family
        final List<Inet4Address> addresses4 = new ArrayList<>(addresses.length);
        final List<Inet6Address> addresses6 = new ArrayList<>(addresses.length);
        for (final InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                addresses4.add((Inet4Address) address);
            }
            else if (DEFAULT_RESOLVE_ADDRESS_TYPES != IPV4_ONLY) {
                addresses6.add((Inet6Address) address);
            }
        }

        final int size4 = addresses4.size();
        final int size6 = addresses6.size();

        if (size4 == 0 && size6 == 0) {
            throw new UnknownHostException("");
        }

        final InetAddress[] result = new InetAddress[size4 + size6];
        if (DEFAULT_RESOLVE_ADDRESS_TYPES != IPV6_PREFERRED) {
            // add ip4 at head
            for (int i = 0; i < size4; i++) {
                result[i] = addresses4.get(i);
            }
            // add ip6 at tail
            for (int i = 0; i < size6; i++) {
                result[size4 + i] = addresses6.get(i);
            }
        }
        else {
            // add ip6 at head
            for (int i = 0; i < size6; i++) {
                result[i] = addresses6.get(i);
            }
            // add ip4 at tail
            for (int i = 0; i < size4; i++) {
                result[size6 + i] = addresses4.get(i);
            }
        }

        return result;
    }

    /**
     * Returns the first address returned by {@link #resolveAll(String)}.
     *
     * @param host name of host to resolve
     * @return first address returned by {@link #resolveAll(String)}
     * @throws UnknownHostException if no IP address for the {@code host} could be found, or if a
     *                              scope_id was specified for a global IPv6 address.
     */
    public static InetAddress resolve(final String host) throws UnknownHostException {
        return resolveAll(host)[0];
    }

    /**
     * Returns {@code true} if any {@link NetworkInterface} supports {@code IPv6}, {@code false}
     * otherwise.
     */
    private static boolean anyInterfaceSupportsIpV6() {
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface iface = interfaces.nextElement();
                final Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    final InetAddress inetAddress = addresses.nextElement();
                    if (inetAddress instanceof Inet6Address && !inetAddress.isAnyLocalAddress() &&
                            !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                        return true;
                    }
                }
            }
        }
        catch (final SocketException e) {
            LOG.debug("Unable to detect if any interface supports IPv6, assuming IPv4-only", e);
        }
        return false;
    }
}
