/*
 * Copyright (c) 2020.
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
package org.drasyl.util;

import org.drasyl.crypto.Crypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.drasyl.util.UrlUtil.createUrl;

/**
 * Utility class for network-related operations.
 */
public final class NetworkUtil {
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

    private NetworkUtil() {
        // util class
    }

    /**
     * Determines the external IPv4 address.
     *
     * @return the external IPv4 address or {@code null} in case of error
     */
    public static Inet4Address getExternalIPv4Address() {
        return getExternalIPAddress(EXTERNAL_IPV4_ADDRESS_SERVICES);
    }

    /**
     * Determines the external IP address.
     * <p>
     * Note: This is a blocking method, because it connects to external server that may react slowly
     * or not at all.
     *
     * @return the external IP address or {@code null} in case of error
     */
    private static <T extends InetAddress> T getExternalIPAddress(URL[] providers) {
        // distribute requests across all available ip check tools
        int randomOffset = Crypto.randomNumber(providers.length);
        for (int i = 0; i < providers.length; i++) {
            URL provider = providers[(i + randomOffset) % providers.length];

            try {
                URLConnection connection = provider.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.getInputStream();

                LOG.debug("Request external ip address from service '{}'...", provider);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = reader.readLine();
                    @SuppressWarnings("unchecked")
                    T address = (T) InetAddress.getByName(response);
                    if (!address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isSiteLocalAddress()) {
                        LOG.debug("Got external ip address '{}' from service '{}'", address, provider);
                        return address;
                    }
                }
            }
            catch (IOException | ClassCastException e) {
                // do nothing, skip to next provider
            }
        }

        // no provider was successful
        return null;
    }

    /**
     * Determines the external IPv6 address.
     *
     * @return the external IPv6 address or {@code null} in case of error
     */
    public static Inet6Address getExternalIPv6Address() {
        return getExternalIPAddress(EXTERNAL_IPV6_ADDRESS_SERVICES);
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
    public static boolean available(int port) {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);

            return true;
        }
        catch (IOException e) {
            // Do nothing
        }

        return false;
    }

    /**
     * Checks if a port is valid or not.
     *
     * @param port port that should be validated.
     * @return true if valid, otherwise false
     */
    public static boolean isValidPort(int port) {
        return port >= MIN_PORT_NUMBER && port <= MAX_PORT_NUMBER;
    }

    /**
     * Checks to see if a specific host:port is available.
     *
     * @param host host name or IP address to check for availability
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the host:port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public static boolean alive(String host, int port) {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        try (Socket s = new Socket(host, port)) {
            PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
            out.println("GET / HTTP/1.1");

            return true;
        }
        catch (IOException e) {
            // Do nothing
        }

        return false;
    }

    /**
     * Returns a list of the IP addresses of all network interfaces of the local computer. If no IP
     * addresses can be obtained, 127.0.0.1 is returned.
     *
     * @return
     */
    public static Set<InetAddress> getAddresses() {
        try {
            Set<InetAddress> addresses = new HashSet<>();

            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();

                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }

                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    InetAddress address = ifaceAddresses.nextElement();
                    if (isValidNonSpecialIPAddress(address)) {
                        addresses.add(address);
                    }
                }
            }

            return addresses;
        }
        catch (SocketException e) {
            return Set.of(InetAddress.getLoopbackAddress());
        }
    }

    /**
     * Check if the given address is a normal IP address that is neither a loopback, link local nor
     * multicast address.
     *
     * @param address the address that should be checked
     * @return true if the address is a normal IP address, false otherwise
     */
    public static boolean isValidNonSpecialIPAddress(InetAddress address) {
        boolean hasScope;
        if (address instanceof Inet6Address) {
            Inet6Address inet6Address = (Inet6Address) address;
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
     * Returns the local host name. If no host name can be determined, <code>null</code> is
     * returned.
     *
     * @return
     */
    public static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            return null;
        }
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
    public static InetAddress createInetAddress(String str) {
        try {
            return InetAddress.getByName(str);
        }
        catch (UnknownHostException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }
}
