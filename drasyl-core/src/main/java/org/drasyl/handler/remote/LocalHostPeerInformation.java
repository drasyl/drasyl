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

import com.google.common.collect.ImmutableSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LocalHostPeerInformation {
    final Set<InetSocketAddress> addresses;

    public LocalHostPeerInformation(final Set<InetSocketAddress> addresses) {
        this.addresses = ImmutableSet.copyOf(addresses);
    }

    public Set<InetSocketAddress> addresses() {
        return addresses;
    }

    void writeTo(final File file) throws IOException {
        try (final FileOutputStream out = new FileOutputStream(file)) {
            for (final InetSocketAddress address : addresses) {
                out.write(serializeAddress(address).getBytes(UTF_8));
                out.write("\n".getBytes(UTF_8));
            }
        }
    }

    public static LocalHostPeerInformation of(final Set<InetSocketAddress> addresses) {
        return new LocalHostPeerInformation(addresses);
    }

    public static LocalHostPeerInformation of(final File file) throws IOException {
        final Set<InetSocketAddress> addresses = new HashSet<>();
        try (final BufferedReader reader = new BufferedReader(new FileReader(file, UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final InetSocketAddress address = deserializeAddress(line);
                addresses.add(address);
            }
        }
        return of(addresses);
    }

    public static LocalHostPeerInformation of(final Path path) throws IOException {
        return of(path.toFile());
    }

    /**
     * Serializes {@code value} to a {@link String}.
     * <p>
     * Implementation from <a href="https://github.com/FasterXML/jackson-databind/blob/2.13/src/main/java/com/fasterxml/jackson/databind/ser/std/InetSocketAddressSerializer.java">Jackson</a>.
     *
     * @param value address to serialize
     * @return {@link String} representation of {@code vlaue}
     */
    @SuppressWarnings("java:S864")
    public static String serializeAddress(final InetSocketAddress value) {
        final InetAddress addr = value.getAddress();
        String str = addr == null ? value.getHostName() : addr.toString().trim();
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

        return str + ":" + value.getPort();
    }

    /**
     * Deserializes {@code value} to a {@link InetSocketAddress}.
     * <p>
     * Implementation from <a href="https://github.com/FasterXML/jackson-databind/blob/2.13/src/main/java/com/fasterxml/jackson/databind/deser/std/FromStringDeserializer.java#L328-L349">Jackson</a>.
     *
     * @param value address to deserialize
     * @return {@link InetSocketAddress} representation of {@code vlaue}
     */
    static InetSocketAddress deserializeAddress(final String value) {
        if (value.startsWith("[")) {
            // bracketed IPv6 (with port number)
            final int i = value.lastIndexOf(']');
            if (i == -1) {
                throw new IllegalArgumentException("Bracketed IPv6 address must contain closing bracket");
            }

            final int j = value.indexOf(':', i);
            final int port = j > -1 ? Integer.parseInt(value.substring(j + 1)) : 0;
            return new InetSocketAddress(value.substring(0, i + 1), port);
        }
        final int ix = value.indexOf(':');
        if (ix >= 0 && value.indexOf(':', ix + 1) < 0) {
            // host:port
            final int port = Integer.parseInt(value.substring(ix + 1));
            return new InetSocketAddress(value.substring(0, ix), port);
        }
        // host or unbracketed IPv6, without port number
        return new InetSocketAddress(value, 0);
    }
}
