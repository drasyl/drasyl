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
import org.drasyl.pipeline.address.InetSocketAddressWrapper;

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
     * Converts {@code s} to an {@link InetSocketAddressWrapper}.
     *
     * @param s string to convert
     * @return {@link InetSocketAddressWrapper} converted from string
     * @throws NullPointerException     if {@code s} is {@code null}
     * @throws IllegalArgumentException if {@code s} does not contain hostname and port or could not
     *                                  be converted to a valid {@link InetSocketAddress}.
     */
    @SuppressWarnings("java:S109")
    public static InetSocketAddressWrapper socketAddressFromString(@NonNull final String s) {
        final String[] split = s.split(":", 2);
        if (split.length != 2) {
            throw new IllegalArgumentException("string must contain hostname and port divided by colon");
        }

        try {
            final String hostname = split[0];
            final int port = Integer.parseInt(split[1]);

            return new InetSocketAddressWrapper(hostname, port);
        }
        catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number format", e);
        }
    }
}
