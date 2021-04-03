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

import com.google.common.net.InetAddresses;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.ThrowingFunction;
import org.drasyl.util.ThrowingSupplier;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static org.drasyl.util.UrlUtil.createUrl;

/**
 * Utility class for network-related operations.
 */
@SuppressWarnings("java:S1192")
public final class NetworkUtil {
    private static final NetworkUtilImpl impl = new NetworkUtilImpl();
    /**
     * The minimum server port number.
     */
    public static final int MIN_PORT_NUMBER = 0;
    /**
     * The maximum server port number.
     */
    public static final int MAX_PORT_NUMBER = 65535;
    private static final Logger LOG = LoggerFactory.getLogger(NetworkUtil.class);
    /**
     * Services for external IPv4 address detection.
     */
    private static final URL[] EXTERNAL_IPV4_ADDRESS_SERVICES = {
            createUrl("https://checkip.amazonaws.com"),
            createUrl("https://ipv4.icanhazip.com"),
            createUrl("https://ipv4.wtfismyip.com/text"),
            createUrl("https://myexternalip.com/raw"),
            createUrl("https://ipecho.net/plain"),
            createUrl("https://ifconfig.me/ip"),
            createUrl("https://ipv4.ipecho.roebert.eu")
    };
    /**
     * Services for external IPv6 address detection.
     */
    private static final URL[] EXTERNAL_IPV6_ADDRESS_SERVICES = {
            createUrl("https://ipv6.icanhazip.com"),
            createUrl("https://ipv6.wtfismyip.com/text"),
            createUrl("https://ipv6.ipecho.roebert.eu")
    };
    public static final Duration LOCAL_ADDRESS_FOR_REMOTE_TIMEOUT = ofSeconds(5);
    public static final Duration EXTERNAL_IP_ADDRESS_TIMEOUT = ofSeconds(5);

    private NetworkUtil() {
        // util class
    }

    /**
     * Determines the external IPv4 address.
     * <p>
     * Note: This is a blocking method, because it connects to external server that may react slowly
     * or not at all.
     *
     * @return the external IPv4 address or {@code null} in case of error
     */
    @SuppressWarnings("unused")
    public static Inet4Address getExternalIPv4Address() {
        return impl.getExternalIPAddress(EXTERNAL_IPV4_ADDRESS_SERVICES);
    }

    /**
     * Determines the external IPv6 address.
     * <p>
     * Note: This is a blocking method, because it connects to external server that may react slowly
     * or not at all.
     *
     * @return the external IPv6 address or {@code null} in case of error
     */
    @SuppressWarnings("unused")
    public static Inet6Address getExternalIPv6Address() {
        return impl.getExternalIPAddress(EXTERNAL_IPV6_ADDRESS_SERVICES);
    }

    /**
     * Checks to see if a specific port is available.
     *
     * <p>
     * Source: <a href= "https://svn.apache.org/viewvc/camel/trunk/components/camel-test/src/main/java/org/apache/camel/test/AvailablePortFinder.java?view=markup#l130">Apache
     * camel</a>
     * </p>
     *
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    @SuppressWarnings("java:S4818")
    public static boolean available(final int port) {
        return impl.available(port);
    }

    /**
     * Checks if a port is valid or not.
     *
     * @param port port that should be validated.
     * @return true if valid, otherwise false
     */
    @SuppressWarnings("unused")
    public static boolean isValidPort(final int port) {
        return impl.isValidPort(port);
    }

    /**
     * Checks to see if a specific host:port is available.
     *
     * @param host host name or IP address to check for availability
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the host:port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public static boolean alive(final String host, final int port) {
        return impl.alive(host, port);
    }

    /**
     * Returns a list of the IP addresses of all network interfaces of the local computer. If no IP
     * addresses can be obtained, the loopback address is returned.
     *
     * @return list of IP addresses of all network interfaces of local computer or loopback address
     * if no address can be obtained
     */
    public static Set<InetAddress> getAddresses() {
        return impl.getAddresses();
    }

    /**
     * Check if the given address is a normal IP address that is neither a loopback, link local nor
     * multicast address.
     *
     * @param address the address that should be checked
     * @return true if the address is a normal IP address, false otherwise
     */
    @SuppressWarnings("java:S1067")
    public static boolean isValidNonSpecialIPAddress(final InetAddress address) {
        final boolean hasScope;
        if (address instanceof Inet6Address) {
            final Inet6Address inet6Address = (Inet6Address) address;
            hasScope = inet6Address.getScopeId() != 0 || inet6Address.getScopedInterface() != null;
        }
        else {
            hasScope = false;
        }

        return !hasScope &&
                !address.isLoopbackAddress() &&
                !address.isLinkLocalAddress() &&
                !address.isMulticastAddress() &&
                ((address instanceof Inet4Address) || (address instanceof Inet6Address));
    }

    /**
     * Returns the local host name. If no host name can be determined, {@code null} is returned.
     *
     * @return the local host name. If no host name can be determined, {@code null} is returned.
     */
    public static String getLocalHostName() {
        return impl.getLocalHostName();
    }

    /**
     * Creates a {@link InetAddress} by parsing the given string.
     *
     * <p> This convenience factory method works as if by invoking the {@link
     * InetAddress#getByName(java.lang.String)} constructor; any {@link UnknownHostException} thrown
     * by the constructor is caught and wrapped in a new {@link IllegalArgumentException} object,
     * which is then thrown.
     *
     * <p> This method is provided for use in situations where it is known that
     * the given string is a legal InetAddress, for example for InetAddress constants declared
     * within a program, and so it would be considered a programming error for the string not to
     * parse as such. The constructors, which throw {@link UnknownHostException} directly, should be
     * used in situations where a InetAddress is being constructed from user input or from some
     * other source that may be prone to errors.  </p>
     *
     * @param str The string to be parsed into a InetAddress
     * @return The new InetAddress
     * @throws IllegalArgumentException if no IP address for the {@code str} could be found, or if a
     *                                  scope_id was specified for a global IPv6 address.
     */
    public static InetAddress createInetAddress(final String str) {
        return impl.createInetAddress(str);
    }

    /**
     * Returns the network prefix length for given address. This is also known as the subnet mask in
     * the context of IPv4 addresses. Typical IPv4 values would be 8 (255.0.0.0), 16 (255.255.0.0)
     * or 24 (255.255.255.0).
     * <p>
     * Typical IPv6 values would be 128 (::1/128) or 10 (fe80::203:baff:fe27:1243/10)
     *
     * @param address The {@code InetAddress} to search with.
     * @return a {@code short} representing the prefix length for the subnet of that address or
     * {@code -1} if there is no network interface with given IP address.
     * @throws NullPointerException If the specified address is {@code null}.
     */
    public static short getNetworkPrefixLength(final InetAddress address) {
        return impl.getNetworkPrefixLength(address);
    }

    /**
     * Checks if two given addresses are in the same network.
     *
     * @param a    first address
     * @param b    second address
     * @param mask the network mask in CIDR notation
     * @return true if the two given addresses are in the same network, false otherwise
     */
    public static boolean sameNetwork(final InetAddress a, final InetAddress b, final short mask) {
        return sameNetwork(a.getAddress(), b.getAddress(), mask);
    }

    /**
     * Checks if two given addresses are in the same network.
     *
     * @param x    first address
     * @param y    second address
     * @param cidr the network mask in CIDR notation
     * @return true if the two given addresses are in the same network, false otherwise
     */
    @SuppressWarnings("java:S1142")
    public static boolean sameNetwork(final byte[] x, final byte[] y, final short cidr) {
        if (x == y) {
            return true;
        }
        if (x == null || y == null) {
            return false;
        }
        if (x.length != y.length) {
            return false;
        }

        // converting the CIDR mask to byte array of correct length
        final byte[] mask = cidr2Netmask(cidr, x.length);

        for (int i = 0; i < x.length; i++) {
            if ((x[i] & mask[i]) != (y[i] & mask[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Converts a given CIDR mask to valid IPv4 or IPv6 network mask.
     *
     * @param cidr       the cidr
     * @param byteLength the final mask length (IPv4 = 4 bytes, IPv6 = 16 bytes)
     * @return the CIDR as network mask byte array
     * @throws IllegalArgumentException if the {@code byteLength} argument is invalid
     */
    @SuppressWarnings("java:S109")
    public static byte[] cidr2Netmask(final short cidr, final int byteLength) {
        if (byteLength != 4 && byteLength != 16) {
            throw new IllegalArgumentException("A valid IP address has always 4 or 16 bytes.");
        }

        final boolean[] bitSet = new boolean[byteLength * 8];
        Arrays.fill(bitSet, 0, cidr, true);

        final byte[] mask = new byte[byteLength];
        for (int i = 0; i < mask.length; i++) {
            byte b = 0;
            for (int j = 0; j < 8; j++) {
                if (bitSet[8 * i + j]) {
                    b |= 1 << (7 - j);
                }
            }
            mask[i] = b;
        }

        return mask;
    }

    public static InetAddress getDefaultGateway() {
        return impl.getDefaultGateway();
    }

    @SuppressWarnings("java:S109")
    public static byte[] getIpv4MappedIPv6AddressBytes(final InetAddress address) {
        if (address instanceof Inet4Address) {
            // create IPv4-mapped IPv6 address
            final byte[] addr = new byte[16];
            addr[10] = (byte) 0xff;
            addr[11] = (byte) 0xff;
            System.arraycopy(address.getAddress(), 0, addr, 12, 4);
            return addr;
        }
        else {
            return address.getAddress();
        }
    }

    /**
     * Establishes a connection to {@code remoteAddress} and returns the local address used for
     * it.If an error occurs, {@code null} is returned.
     *
     * @param remoteAddress The remote address
     * @return local address used to connect to {@code remoteAddress}, or {@code null} in case of
     * error
     */
    @SuppressWarnings("unused")
    public static InetAddress getLocalAddressForRemoteAddress(final InetSocketAddress remoteAddress) {
        return impl.getLocalAddressForRemoteAddress(remoteAddress);
    }

    /**
     * Private implementation class pointed to some static methods.
     */
    @SuppressWarnings("java:S2972")
    static class NetworkUtilImpl {
        private final ThrowingSupplier<InputStream, IOException> defaultGatewayProvider;
        private final ThrowingFunction<URL, URLConnection, IOException> urlConnectionProvider;
        private final Supplier<Socket> socketSupplier;

        NetworkUtilImpl(final ThrowingSupplier<InputStream, IOException> defaultGatewayProvider,
                        final ThrowingFunction<URL, URLConnection, IOException> urlConnectionProvider,
                        final Supplier<Socket> socketSupplier) {
            this.defaultGatewayProvider = defaultGatewayProvider;
            this.urlConnectionProvider = urlConnectionProvider;
            this.socketSupplier = socketSupplier;
        }

        NetworkUtilImpl() {
            this(
                    () -> {
                        final Process result = Runtime.getRuntime().exec("netstat -rn");
                        return result.getInputStream();
                    },
                    URL::openConnection,
                    Socket::new
            );
        }

        @SuppressWarnings("java:S134")
        <T extends InetAddress> T getExternalIPAddress(final URL[] providers) {
            // distribute requests across all available ip check tools
            final int randomOffset = RandomUtil.randomInt(providers.length);
            for (int i = 0; i < providers.length; i++) {
                final URL provider = providers[(i + randomOffset) % providers.length];

                try {
                    final URLConnection connection = urlConnectionProvider.apply(provider);
                    connection.setConnectTimeout((int) EXTERNAL_IP_ADDRESS_TIMEOUT.toMillis());
                    connection.setReadTimeout((int) EXTERNAL_IP_ADDRESS_TIMEOUT.toMillis());

                    LOG.debug("Request external ip address from service '{}'...", provider);

                    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), UTF_8))) {
                        final String response = reader.readLine();
                        @SuppressWarnings("unchecked") final T address = (T) InetAddress.getByName(response);
                        if (!address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isSiteLocalAddress()) {
                            LOG.debug("Got external ip address '{}' from service '{}'", address, provider);
                            return address;
                        }
                    }
                }
                catch (final IOException e) {
                    LOG.debug("I/O error occurred.", e);
                }
                catch (final ClassCastException e) {
                    LOG.debug("Address returned by provider has the wrong type.", e);
                }
            }

            // no provider was successful
            return null;
        }

        boolean available(final int port) {
            if (!isValidPort(port)) {
                throw new IllegalArgumentException("Invalid port: " + port);
            }

            try (final ServerSocket ss = new ServerSocket(port)) {
                ss.setReuseAddress(true);

                return true;
            }
            catch (final IOException e) {
                LOG.debug("I/O error occurred.", e);
            }

            return false;
        }

        boolean isValidPort(final int port) {
            return port >= MIN_PORT_NUMBER && port <= MAX_PORT_NUMBER;
        }

        boolean alive(final String host, final int port) {
            if (!isValidPort(port)) {
                throw new IllegalArgumentException("Invalid port: " + port);
            }

            try (final Socket s = new Socket(host, port)) {
                final PrintWriter out = new PrintWriter(s.getOutputStream(), true, UTF_8);
                out.println("GET / HTTP/1.1");

                return true;
            }
            catch (final IOException e) {
                LOG.debug("I/O error occurred.", e);
            }

            return false;
        }

        @SuppressWarnings("java:S134")
        Set<InetAddress> getAddresses() {
            try {
                final Set<InetAddress> addresses = new HashSet<>();

                final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
                while (ifaces.hasMoreElements()) {
                    final NetworkInterface iface = ifaces.nextElement();

                    if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                        continue;
                    }

                    final Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                    while (ifaceAddresses.hasMoreElements()) {
                        final InetAddress address = ifaceAddresses.nextElement();
                        if (isValidNonSpecialIPAddress(address)) {
                            addresses.add(address);
                        }
                    }
                }

                return addresses;
            }
            catch (final SocketException e) {
                LOG.debug("I/O error occurred.", e);
                return Set.of(InetAddress.getLoopbackAddress());
            }
        }

        String getLocalHostName() {
            try {
                return InetAddress.getLocalHost().getHostName();
            }
            catch (final UnknownHostException e) {
                LOG.debug("No local IP address could be found.", e);
                return null;
            }
        }

        InetAddress createInetAddress(final String str) {
            try {
                return InetAddress.getByName(str);
            }
            catch (final UnknownHostException e) {
                throw new IllegalArgumentException("Host could not be resolved.", e);
            }
        }

        @SuppressWarnings("java:S134")
        short getNetworkPrefixLength(final InetAddress address) {
            try {
                final NetworkInterface networkInterface = NetworkInterface.getByInetAddress(address);
                if (networkInterface != null) {
                    for (final InterfaceAddress ifaceAddress : networkInterface.getInterfaceAddresses()) {
                        if (address.equals(ifaceAddress.getAddress())) {
                            return ifaceAddress.getNetworkPrefixLength();
                        }
                    }
                }

                // no network interface with the specified IP address found
                return -1;
            }
            catch (final SocketException e) {
                LOG.debug("I/O error occurred.", e);
                return -1;
            }
        }

        @SuppressWarnings({ "java:S1142", "UnstableApiUsage" })
        InetAddress getDefaultGateway() {
            // get line with default gateway address from "netstat"
            String line;
            try {
                final InputStream inputStream = defaultGatewayProvider.get();
                final BufferedReader output = new BufferedReader(new InputStreamReader(inputStream, UTF_8));

                while ((line = output.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("default") || line.startsWith("0.0.0.0")) {
                        break;
                    }
                }
            }
            catch (final IOException e) {
                LOG.debug("Unable to determine default gateway address.", e);
                return null;
            }

            if (line == null) {
                return null;
            }

            // get token with default gateway address
            final StringTokenizer tokenizer = new StringTokenizer(line);
            while (tokenizer.hasMoreTokens()) {
                final String token = tokenizer.nextToken();
                if (InetAddresses.isInetAddress(token)) {
                    try {
                        final InetAddress address = InetAddress.getByName(token);
                        if (!address.isLoopbackAddress() && !address.isAnyLocalAddress() && address.isSiteLocalAddress()) {
                            return address;
                        }
                    }
                    catch (final UnknownHostException e) {
                        LOG.debug("No IP address for `" + token + "` could be found.", e);
                    }
                }
            }

            return null;
        }

        InetAddress getLocalAddressForRemoteAddress(final InetSocketAddress remoteAddress) {
            try (final Socket socket = socketSupplier.get()) {
                socket.connect(remoteAddress, (int) LOCAL_ADDRESS_FOR_REMOTE_TIMEOUT.toMillis());
                return socket.getLocalAddress();
            }
            catch (final IOException e) {
                LOG.debug("Error occurred during the connection.", e);
                return null;
            }
        }
    }
}
