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
package org.drasyl.util;

import org.drasyl.annotation.NonNull;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;

/**
 * Utility class for operations on {@link URL}s.
 */
public final class InetSocketAddressUtil {
    private InetSocketAddressUtil() {
        // util class
    }

    /**
     * Convert a {@link String} to a {@link InetSocketAddress}.
     * <p>
     * Implementation from <a href="https://github.com/FasterXML/jackson-databind/blob/2.13/src/main/java/com/fasterxml/jackson/databind/deser/std/FromStringDeserializer.java#L328-L349">Jackson</a>.
     *
     * @param s address to deserialize
     * @return {@link InetSocketAddress} representation of {@code s}
     */
    @SuppressWarnings("java:S109")
    public static InetSocketAddress socketAddressFromString(@NonNull final String s) {
        if (s.startsWith("[")) {
            // bracketed IPv6 (with port number)
            final int i = s.lastIndexOf(']');
            if (i == -1) {
                throw new IllegalArgumentException("Bracketed IPv6 address must contain closing bracket");
            }

            final int j = s.indexOf(':', i);
            final int port = j > -1 ? Integer.parseInt(s.substring(j + 1)) : 0;
            return new InetSocketAddress(s.substring(0, i + 1), port);
        }
        final int ix = s.indexOf(':');
        if (ix >= 0 && s.indexOf(':', ix + 1) < 0) {
            // host:port
            final int port = Integer.parseInt(s.substring(ix + 1));
            return new InetSocketAddress(s.substring(0, ix), port);
        }
        // host or unbracketed IPv6, without port number
        return new InetSocketAddress(s, 0);
    }

    /**
     * Convert a {@link InetSocketAddress} to a {@link String}.
     * <p>
     * Implementation from <a href="https://github.com/FasterXML/jackson-databind/blob/2.13/src/main/java/com/fasterxml/jackson/databind/deser/std/FromStringDeserializer.java#L328-L349">Jackson</a>.
     *
     * @param s address to deserialize
     * @return {@link String} representation of {@code s}
     */
    @SuppressWarnings("java:S109")
    public static String socketAddressToString(@NonNull final InetSocketAddress s) {
        final InetAddress addr = s.getAddress();
        String str = addr == null ? s.getHostName() : addr.toString().trim();
        final int ix = str.indexOf('/');
        if (ix >= 0) {
            if (ix == 0) { // missing host name; use address
                str = addr instanceof Inet6Address
                        ? "[" + str.substring(1) + "]" // bracket IPv6 addresses with
                        : str.substring(1);
            }
            else { // otherwise use name
                str = str.substring(0, ix);
            }
        }

        return str + ":" + s.getPort();
    }

    /**
     * Checks equality of {@code a} and {@code b}. In comparison to {@link
     * InetSocketAddress#equals(Object)}, this method can also compare resolved with unresolved
     * addresses.
     *
     * @param a first address to check
     * @param b second address to check
     * @return {@code true} if both addresses are equal
     */
    public static boolean equalSocketAddress(@NonNull final InetSocketAddress a,
                                             @NonNull final InetSocketAddress b) {
        if (a.isUnresolved() == b.isUnresolved()) {
            return a.equals(b);
        }
        return a.getPort() == b.getPort() && a.getHostString().equals(b.getHostString());
    }
}
