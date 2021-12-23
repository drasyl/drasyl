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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.drasyl.util.InetSocketAddressUtil.socketAddressFromString;
import static org.drasyl.util.InetSocketAddressUtil.socketAddressToString;

public class LocalHostPeerInformation {
    final Set<InetSocketAddress> addresses;

    public LocalHostPeerInformation(final Set<InetSocketAddress> addresses) {
        this.addresses = Set.copyOf(addresses);
    }

    public Set<InetSocketAddress> addresses() {
        return addresses;
    }

    void writeTo(final File file) throws IOException {
        try (final FileOutputStream out = new FileOutputStream(file)) {
            for (final InetSocketAddress address : addresses) {
                out.write(socketAddressToString(address).getBytes(UTF_8));
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
                final InetSocketAddress address = socketAddressFromString(line);
                addresses.add(address);
            }
        }
        return of(addresses);
    }

    public static LocalHostPeerInformation of(final Path path) throws IOException {
        return of(path.toFile());
    }
}
